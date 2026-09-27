package com.jose.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;

import com.jose.subscriptions.billing.BillingScheduler;
import com.jose.subscriptions.billing.BillingScheduler.RunSummary;
import com.jose.subscriptions.support.IntegrationTest;

class BillingIT extends IntegrationTest {

    @Autowired
    BillingScheduler scheduler;

    @Test
    void renewalInvoicesTheNextPeriodAndAdvancesIt() throws Exception {
        long customer = customerWith("PREMIUM");

        clock.set(Instant.parse("2026-02-01T02:00:00Z")); // el cron de las 02:00 tras vencer
        assertThat(scheduler.runOnce()).isEqualTo(new RunSummary(1, 1, 0, 0, 0));

        mvc.perform(get("/api/customers/{id}/subscription", customer))
                .andExpect(jsonPath("$.currentPeriodStart").value("2026-02-01T00:00:00Z"))
                .andExpect(jsonPath("$.currentPeriodEnd").value("2026-03-01T00:00:00Z"));
        mvc.perform(get("/api/customers/{id}/invoices", customer))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].kind").value("RENEWAL"))
                .andExpect(jsonPath("$[0].amountCents").value(990))
                .andExpect(jsonPath("$[0].periodStart").value("2026-02-01T00:00:00Z"));
    }

    @Test
    void runningTheSchedulerTwiceDoesNotDuplicateInvoices() throws Exception {
        customerWith("PREMIUM");
        clock.set(Instant.parse("2026-02-01T02:00:00Z"));

        scheduler.runOnce();
        assertThat(scheduler.runOnce().due()).isZero();
        assertThat(invoiceCount()).isEqualTo(2); // INITIAL + una RENEWAL
    }

    @Test
    void concurrentRunsNeverBillTheSamePeriodTwice() throws Exception {
        for (int i = 0; i < 5; i++) {
            customerWith("PREMIUM", "cliente" + i + "@example.com");
        }
        clock.set(Instant.parse("2026-02-01T02:00:00Z"));

        // Cuatro ejecuciones a la vez (p. ej. varias instancias de la app con el mismo cron).
        List<RunSummary> runs = SubscriptionFlowIT.runConcurrently(4, scheduler::runOnce);

        assertThat(runs.stream().mapToInt(RunSummary::failed).sum()).isZero();
        assertThat(runs.stream().mapToInt(RunSummary::renewed).sum()).isEqualTo(5);
        assertThat(invoiceCount()).isEqualTo(10); // 5 INITIAL + 5 RENEWAL, ni una más
    }

    @Test
    void databaseRejectsADuplicateInvoiceForTheSamePeriod() throws Exception {
        customerWith("PREMIUM");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO invoices (subscription_id, kind, amount_cents, period_start, period_end)
                SELECT subscription_id, 'RENEWAL', amount_cents, period_start, period_end FROM invoices"""))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void catchesUpSeveralMissedPeriodsInOneRun() throws Exception {
        long customer = customerWith("METAL");
        clock.set(Instant.parse("2026-04-15T00:00:00Z")); // proceso parado casi tres meses

        scheduler.runOnce();

        mvc.perform(get("/api/customers/{id}/subscription", customer))
                .andExpect(jsonPath("$.currentPeriodEnd").value("2026-05-01T00:00:00Z"));
        mvc.perform(get("/api/customers/{id}/invoices", customer))
                .andExpect(jsonPath("$", hasSize(4))) // enero (INITIAL) + feb, mar, abr
                .andExpect(jsonPath("$[0].periodStart").value("2026-04-01T00:00:00Z"));
    }

    @Test
    void billingDayDoesNotDriftAtTheEndOfTheMonth() throws Exception {
        clock.set(Instant.parse("2026-01-31T09:00:00Z"));
        long customer = customerWith("PREMIUM");

        clock.set(Instant.parse("2026-03-31T10:00:00Z"));
        scheduler.runOnce();

        mvc.perform(get("/api/customers/{id}/invoices", customer))
                .andExpect(jsonPath("$[0].periodStart").value("2026-03-31T09:00:00Z")) // no el 28
                .andExpect(jsonPath("$[1].periodStart").value("2026-02-28T09:00:00Z"));
    }

    @Test
    void cancelledSubscriptionStaysActiveUntilPeriodEndAndThenStops() throws Exception {
        long customer = customerWith("PREMIUM");
        long subscription = 1;

        clock.advance(Duration.ofDays(10));
        mvc.perform(post("/api/subscriptions/{id}/cancel", subscription))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));

        mvc.perform(post("/api/subscriptions/{id}/cancel", subscription))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_SCHEDULED"));

        clock.set(Instant.parse("2026-02-01T02:00:00Z"));
        assertThat(scheduler.runOnce()).isEqualTo(new RunSummary(1, 0, 1, 0, 0));

        mvc.perform(get("/api/subscriptions/{id}", subscription))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(invoiceCount()).isEqualTo(1); // no se ha facturado el periodo siguiente

        // Una vez terminada, el cliente puede volver a suscribirse.
        mvc.perform(post("/api/customers/{id}/subscriptions", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\": \"METAL\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void whileAnotherInstanceHoldsTheLockTheCronDoesNothing() throws Exception {
        customerWith("PREMIUM");
        clock.set(Instant.parse("2026-02-01T02:00:00Z"));
        jdbc.update("""
                INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
                VALUES (?, timezone('utc', now()) + interval '10 minutes', timezone('utc', now()), 'otra-instancia')
                ON CONFLICT (name) DO UPDATE SET lock_until = EXCLUDED.lock_until, locked_by = EXCLUDED.locked_by
                """, BillingScheduler.LOCK_NAME);

        scheduler.renewDueSubscriptions(); // pasa por el proxy de ShedLock, como el @Scheduled

        assertThat(invoiceCount()).isEqualTo(1); // solo la INITIAL: no se ha renovado nada
    }

    @Test
    void theLockIsHeldForAtLeastAMinuteAfterTheRun() throws Exception {
        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
        customerWith("PREMIUM", "ana@example.com");
        clock.set(Instant.parse("2026-02-01T02:00:00Z"));

        scheduler.renewDueSubscriptions();
        assertThat(invoiceCount()).isEqualTo(2);
        var heldFor = jdbc.queryForObject(
                "SELECT extract(epoch FROM lock_until - locked_at) FROM shedlock WHERE name = ?",
                Double.class, BillingScheduler.LOCK_NAME);
        assertThat(heldFor).isGreaterThanOrEqualTo(60.0);

        // Otra instancia con el reloj algo retrasado dispara el cron justo después: se lo salta.
        clock.set(Instant.parse("2026-01-15T00:00:00Z"));
        customerWith("PREMIUM", "luis@example.com");
        clock.set(Instant.parse("2026-02-15T02:00:00Z"));
        scheduler.renewDueSubscriptions();
        assertThat(invoiceCount()).isEqualTo(3); // la de Luis sigue pendiente

        // runOnce() no pasa por el bloqueo: es lo que usaría una ejecución manual.
        assertThat(scheduler.runOnce().renewed()).isEqualTo(1);
        assertThat(invoiceCount()).isEqualTo(4);
    }

    // --- helpers --------------------------------------------------------------------------

    private long customerWith(String plan) throws Exception {
        return customerWith(plan, "ana@example.com");
    }

    private long customerWith(String plan, String email) throws Exception {
        var json = mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"fullName\": \"Cliente\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(post("/api/customers/{id}/subscriptions", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\": \"%s\"}".formatted(plan)))
                .andExpect(status().isCreated());
        return id;
    }

    private int invoiceCount() {
        return jdbc.queryForObject("SELECT count(*) FROM invoices", Integer.class);
    }
}
