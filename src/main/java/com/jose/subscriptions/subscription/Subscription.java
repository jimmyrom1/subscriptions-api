package com.jose.subscriptions.subscription;

import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.jose.subscriptions.customer.Customer;
import com.jose.subscriptions.plan.Plan;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "billing_cycle", nullable = false)
    private int billingCycle;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Bloqueo optimista: dos escrituras concurrentes sobre la misma fila no se pisan. */
    @Version
    private int version;

    protected Subscription() {
    }

    public static Subscription start(Customer customer, Plan plan, Instant now) {
        var s = new Subscription();
        s.customer = customer;
        s.plan = plan;
        s.status = SubscriptionStatus.ACTIVE;
        s.startedAt = now;
        s.billingCycle = 1;
        s.currentPeriodStart = now;
        s.currentPeriodEnd = periodEnd(now, 1);
        return s;
    }

    /**
     * Fin del periodo n contado siempre desde el alta. Si se sumara un mes al fin anterior,
     * un alta el 31 de enero acabaría facturándose el 28 de cada mes (31 ene → 28 feb → 28 mar).
     */
    static Instant periodEnd(Instant startedAt, int cycle) {
        return startedAt.atOffset(ZoneOffset.UTC).plusMonths(cycle).toInstant();
    }

    public boolean isDue(Instant now) {
        return status == SubscriptionStatus.ACTIVE && !currentPeriodEnd.isAfter(now);
    }

    public boolean isCancellationScheduled() {
        return status == SubscriptionStatus.ACTIVE && cancelledAt != null;
    }

    /** Pasa al siguiente periodo mensual. */
    public void advancePeriod() {
        billingCycle++;
        currentPeriodStart = currentPeriodEnd;
        currentPeriodEnd = periodEnd(startedAt, billingCycle);
    }

    public void scheduleCancellation(Instant now) {
        this.cancelledAt = now;
    }

    public void terminate() {
        this.status = SubscriptionStatus.CANCELLED;
    }

    public void changePlan(Plan newPlan) {
        this.plan = newPlan;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Plan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public int getBillingCycle() {
        return billingCycle;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
