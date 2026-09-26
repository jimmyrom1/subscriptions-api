package com.jose.subscriptions.billing;

import java.time.Instant;

public record InvoiceResponse(
        Long id,
        Long subscriptionId,
        String kind,
        int amountCents,
        String currency,
        Instant periodStart,
        Instant periodEnd,
        Instant createdAt) {

    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(i.getId(), i.getSubscription().getId(), i.getKind().name(),
                i.getAmountCents(), "EUR", i.getPeriodStart(), i.getPeriodEnd(), i.getCreatedAt());
    }
}
