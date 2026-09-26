package com.jose.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jose.subscriptions.subscription.SubscriptionService;
import com.jose.subscriptions.support.IntegrationTest;

class SubscriptionFlowIT extends IntegrationTest {

    @Autowired
    SubscriptionService subscriptionService;

    @Test
    void listsActivePlansCheapestFirst() throws Exception {
        jdbc.update("UPDATE plans SET active = FALSE WHERE code = 'METAL'");
        mvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[1].monthlyPriceCents").value(990));
    }

    @Test
    void createsCustomerAndValidatesInput() throws Exception {
        postJson("/api/customers", """
                {"email": "Ana@Example.com", "fullName": "  Ana García "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.fullName").value("Ana García"));

        postJson("/api/customers", """
                {"email": "no-es-un-email", "fullName": ""}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.fullName", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        postJson("/api/customers", """
                {"email": "ana@example.com", "fullName": "Otra Ana"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void subscribeCreatesSubscriptionAndFirstInvoice() throws Exception {
        long customer = createCustomer("ana@example.com");

        subscribe(customer, "PREMIUM")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentPeriodEnd").value("2026-02-01T00:00:00Z"));

        mvc.perform(get("/api/customers/{id}/invoices", customer))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].kind").value("INITIAL"))
                .andExpect(jsonPath("$[0].amountCents").value(990));
    }

    @Test
    void customerCannotHaveTwoActiveSubscriptions() throws Exception {
        long customer = createCustomer("ana@example.com");
        subscribe(customer, "PREMIUM").andExpect(status().isCreated());

        subscribe(customer, "METAL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_SUBSCRIPTION_EXISTS"));
    }

    @Test
    void partialUniqueIndexProtectsAgainstConcurrentSubscriptions() throws Exception {
        long customer = createCustomer("ana@example.com");

        // Dos altas simultáneas: ambas pueden pasar la comprobación del servicio,
        // pero el índice parcial one_active_sub_per_customer solo deja entrar a una.
        var results = runConcurrently(8, () -> {
            try {
                subscriptionService.subscribe(customer, "PREMIUM");
                return "ok";
            } catch (RuntimeException e) {
                return "rejected";
            }
        });
        assertThat(results).containsOnlyOnce("ok");
        assertThat(count("SELECT count(*) FROM subscriptions WHERE status = 'ACTIVE'")).isEqualTo(1);

        // Y a nivel de SQL, sin pasar por la aplicación:
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO subscriptions (customer_id, plan_id, status, started_at,
                                           current_period_start, current_period_end)
                VALUES (?, 1, 'ACTIVE', now(), now(), now() + interval '1 month')""", customer))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void upgradeMidPeriodGeneratesAProratedInvoice() throws Exception {
        long customer = createCustomer("ana@example.com");
        long subscription = subscriptionId(subscribe(customer, "PREMIUM"));

        clock.advance(Duration.ofHours(372)); // 15,5 de los 31 días de enero: queda la mitad

        changePlan(subscription, "METAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.planCode").value("METAL"))
                .andExpect(jsonPath("$.prorationInvoice.kind").value("PRORATION"))
                .andExpect(jsonPath("$.prorationInvoice.amountCents").value(350)); // (1690 − 990) / 2

        // Downgrade en el mismo instante: abono por el mismo tramo. Dos prorrateos con el mismo
        // period_start son válidos (el índice de idempotencia solo cubre INITIAL y RENEWAL).
        changePlan(subscription, "PREMIUM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prorationInvoice.amountCents").value(-350));

        mvc.perform(get("/api/customers/{id}/invoices", customer))
                .andExpect(jsonPath("$[*].amountCents", containsInAnyOrder(990, 350, -350)));
    }

    @Test
    void cannotMoveToTheSameOrAnInactivePlan() throws Exception {
        long customer = createCustomer("ana@example.com");
        long subscription = subscriptionId(subscribe(customer, "PREMIUM"));

        changePlan(subscription, "PREMIUM")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAME_PLAN"));

        jdbc.update("UPDATE plans SET active = FALSE WHERE code = 'METAL'");
        changePlan(subscription, "METAL")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLAN_INACTIVE"));
    }

    @Test
    void unknownResourcesAndMalformedRequestsReturnJsonErrors() throws Exception {
        subscribe(999, "PREMIUM")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        mvc.perform(post("/api/subscriptions/1/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));

        postJson("/api/customers", "{ esto no es json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        long customer = createCustomer("ana@example.com");
        subscribe(customer, "premium") // el código de plan va en mayúsculas
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.planCode", notNullValue()));
    }

    @Test
    void openApiDocsAndHealthAreExposed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/subscriptions/{id}/plan']", notNullValue()));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // --- helpers --------------------------------------------------------------------------

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createCustomer(String email) throws Exception {
        var json = postJson("/api/customers", """
                {"email": "%s", "fullName": "Cliente de prueba"}""".formatted(email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions subscribe(long customer, String plan) throws Exception {
        return postJson("/api/customers/" + customer + "/subscriptions", """
                {"planCode": "%s"}""".formatted(plan));
    }

    private ResultActions changePlan(long subscription, String plan) throws Exception {
        return mvc.perform(put("/api/subscriptions/{id}/plan", subscription)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\": \"%s\"}".formatted(plan)));
    }

    static long subscriptionId(ResultActions result) throws Exception {
        var json = result.andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(task));
            }
            List<T> results = new ArrayList<>();
            for (var f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
