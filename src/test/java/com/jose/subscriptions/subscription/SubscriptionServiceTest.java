package com.jose.subscriptions.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.jose.subscriptions.billing.BillingService;
import com.jose.subscriptions.billing.Invoice;
import com.jose.subscriptions.common.BusinessException;
import com.jose.subscriptions.customer.Customer;
import com.jose.subscriptions.customer.CustomerRepository;
import com.jose.subscriptions.plan.Plan;
import com.jose.subscriptions.plan.PlanRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z"); // periodo de 31 días
    private static final Instant MID_PERIOD = Instant.parse("2026-01-16T12:00:00Z"); // quedan 15,5 días

    @Mock SubscriptionRepository subscriptions;
    @Mock CustomerRepository customers;
    @Mock PlanRepository plans;
    @Mock BillingService billing;

    private final Customer customer = withId(new Customer("ana@test.com", "Ana", START), 1L);
    private final Plan premium = withId(new Plan("PREMIUM", "Premium", 990), 2L);
    private final Plan metal = withId(new Plan("METAL", "Metal", 1690), 3L);

    private SubscriptionService serviceAt(Instant now) {
        return new SubscriptionService(subscriptions, customers, plans, billing, Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void plans() {
        org.mockito.Mockito.lenient().when(plans.findByCode("PREMIUM")).thenReturn(Optional.of(premium));
        org.mockito.Mockito.lenient().when(plans.findByCode("METAL")).thenReturn(Optional.of(metal));
    }

    // --- Alta -----------------------------------------------------------------------------

    @Test
    void subscribeCreatesSubscriptionAndInitialInvoice() {
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(subscriptions.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var s = serviceAt(START).subscribe(1L, "PREMIUM");

        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.getCurrentPeriodEnd()).isEqualTo("2026-02-01T00:00:00Z");
        verify(billing).issueInitialInvoice(s);
    }

    @Test
    void subscribeFailsWith409WhenCustomerAlreadyHasAnActiveSubscription() {
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(subscriptions.existsByCustomerIdAndStatus(1L, SubscriptionStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> serviceAt(START).subscribe(1L, "PREMIUM"))
                .isInstanceOf(BusinessException.Conflict.class)
                .extracting("code").isEqualTo("ACTIVE_SUBSCRIPTION_EXISTS");
        verify(subscriptions, never()).saveAndFlush(any());
        verifyNoInteractions(billing);
    }

    @Test
    void subscribeToInactivePlanIsA422() {
        metal.setActive(false);
        when(customers.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> serviceAt(START).subscribe(1L, "METAL"))
                .isInstanceOf(BusinessException.RuleViolation.class)
                .extracting("code").isEqualTo("PLAN_INACTIVE");
    }

    @Test
    void subscribeUnknownCustomerOrPlanIsA404() {
        when(customers.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> serviceAt(START).subscribe(99L, "PREMIUM"))
                .isInstanceOf(BusinessException.NotFound.class);

        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        when(plans.findByCode("GOLD")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> serviceAt(START).subscribe(1L, "GOLD"))
                .isInstanceOf(BusinessException.NotFound.class)
                .extracting("code").isEqualTo("PLAN_NOT_FOUND");
    }

    // --- Cambio de plan -------------------------------------------------------------------

    @Test
    void upgradeMidPeriodChargesTheProratedDifference() {
        var s = existing(premium);
        var invoice = mock(Invoice.class);
        when(billing.issueProrationInvoice(eq(s), anyInt(), eq(MID_PERIOD))).thenReturn(invoice);

        var change = serviceAt(MID_PERIOD).changePlan(10L, "METAL");

        // (1690 − 990) × 15,5 / 31 = 350
        verify(billing).issueProrationInvoice(s, 350, MID_PERIOD);
        assertThat(change.subscription().getPlan()).isSameAs(metal);
        assertThat(change.prorationInvoice()).isSameAs(invoice);
    }

    @Test
    void downgradeMidPeriodIssuesACredit() {
        var s = existing(metal);
        serviceAt(MID_PERIOD).changePlan(10L, "PREMIUM");
        verify(billing).issueProrationInvoice(s, -350, MID_PERIOD);
    }

    @Test
    void changingToTheSamePlanIsA422() {
        existing(premium);
        assertThatThrownBy(() -> serviceAt(MID_PERIOD).changePlan(10L, "PREMIUM"))
                .isInstanceOf(BusinessException.RuleViolation.class)
                .extracting("code").isEqualTo("SAME_PLAN");
        verifyNoInteractions(billing);
    }

    @Test
    void changingToAnInactivePlanIsA422() {
        existing(premium);
        metal.setActive(false);
        assertThatThrownBy(() -> serviceAt(MID_PERIOD).changePlan(10L, "METAL"))
                .isInstanceOf(BusinessException.RuleViolation.class)
                .extracting("code").isEqualTo("PLAN_INACTIVE");
    }

    @Test
    void cannotChangePlanOnceCancellationIsScheduled() {
        var s = existing(premium);
        s.scheduleCancellation(MID_PERIOD);
        assertThatThrownBy(() -> serviceAt(MID_PERIOD).changePlan(10L, "METAL"))
                .isInstanceOf(BusinessException.Conflict.class)
                .extracting("code").isEqualTo("CANCELLATION_SCHEDULED");
    }

    @Test
    void cannotChangePlanWhileRenewalIsPending() {
        existing(premium);
        assertThatThrownBy(() -> serviceAt(Instant.parse("2026-02-01T00:00:01Z")).changePlan(10L, "METAL"))
                .isInstanceOf(BusinessException.Conflict.class)
                .extracting("code").isEqualTo("RENEWAL_PENDING");
    }

    // --- Cancelación ----------------------------------------------------------------------

    @Test
    void cancelKeepsTheSubscriptionActiveUntilPeriodEnd() {
        existing(premium);
        var s = serviceAt(MID_PERIOD).cancel(10L);

        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.getCancelledAt()).isEqualTo(MID_PERIOD);
        assertThat(s.isCancellationScheduled()).isTrue();
    }

    @Test
    void cancellingTwiceIsA409() {
        existing(premium);
        var service = serviceAt(MID_PERIOD);
        service.cancel(10L);
        assertThatThrownBy(() -> service.cancel(10L))
                .isInstanceOf(BusinessException.Conflict.class)
                .extracting("code").isEqualTo("CANCELLATION_SCHEDULED");
    }

    @Test
    void cannotCancelATerminatedSubscription() {
        existing(premium).terminate();
        assertThatThrownBy(() -> serviceAt(MID_PERIOD).cancel(10L))
                .isInstanceOf(BusinessException.Conflict.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_NOT_ACTIVE");
    }

    @Test
    void unknownSubscriptionIsA404() {
        when(subscriptions.findWithPlanById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> serviceAt(MID_PERIOD).cancel(404L))
                .isInstanceOf(BusinessException.NotFound.class);
    }

    // --- helpers --------------------------------------------------------------------------

    private Subscription existing(Plan plan) {
        var s = withId(Subscription.start(customer, plan, START), 10L);
        when(subscriptions.findWithPlanById(10L)).thenReturn(Optional.of(s));
        return s;
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
