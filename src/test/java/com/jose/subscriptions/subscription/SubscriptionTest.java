package com.jose.subscriptions.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.jose.subscriptions.customer.Customer;
import com.jose.subscriptions.plan.Plan;

class SubscriptionTest {

    private final Customer customer = new Customer("ana@test.com", "Ana", Instant.EPOCH);
    private final Plan premium = new Plan("PREMIUM", "Premium", 990);

    @Test
    void periodsAreAnchoredToTheStartDateAndDoNotDrift() {
        var s = Subscription.start(customer, premium, Instant.parse("2026-01-31T10:00:00Z"));
        assertThat(s.getCurrentPeriodEnd()).isEqualTo("2026-02-28T10:00:00Z");

        s.advancePeriod();
        assertThat(s.getCurrentPeriodStart()).isEqualTo("2026-02-28T10:00:00Z");
        assertThat(s.getCurrentPeriodEnd()).isEqualTo("2026-03-31T10:00:00Z"); // no el 28

        s.advancePeriod();
        assertThat(s.getCurrentPeriodEnd()).isEqualTo("2026-04-30T10:00:00Z");
        assertThat(s.getBillingCycle()).isEqualTo(3);
    }

    @Test
    void isDueOnlyWhenActiveAndPeriodHasEnded() {
        var s = Subscription.start(customer, premium, Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(s.isDue(Instant.parse("2026-01-31T23:59:59Z"))).isFalse();
        assertThat(s.isDue(Instant.parse("2026-02-01T00:00:00Z"))).isTrue();

        s.terminate();
        assertThat(s.isDue(Instant.parse("2026-03-01T00:00:00Z"))).isFalse();
    }

    @Test
    void cancellationIsScheduledUntilTerminated() {
        var s = Subscription.start(customer, premium, Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(s.isCancellationScheduled()).isFalse();

        s.scheduleCancellation(Instant.parse("2026-01-10T00:00:00Z"));
        assertThat(s.isCancellationScheduled()).isTrue();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        s.terminate();
        assertThat(s.isCancellationScheduled()).isFalse();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }
}
