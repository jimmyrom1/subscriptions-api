package com.jose.subscriptions.billing;

import java.time.Instant;

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

import com.jose.subscriptions.subscription.Subscription;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceKind kind;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Invoice() {
    }

    public Invoice(Subscription subscription, InvoiceKind kind, int amountCents,
                   Instant periodStart, Instant periodEnd, Instant createdAt) {
        this.subscription = subscription;
        this.kind = kind;
        this.amountCents = amountCents;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public InvoiceKind getKind() {
        return kind;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
