package com.jose.subscriptions.subscription.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.jose.subscriptions.billing.InvoiceResponse;
import com.jose.subscriptions.subscription.Subscription;
import com.jose.subscriptions.subscription.SubscriptionService.PlanChange;

/** DTOs de entrada y salida. Las entidades JPA nunca salen del servicio tal cual. */
public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record PlanRequest(
            @NotBlank @Pattern(regexp = "[A-Z_]{1,20}", message = "debe ser un código de plan, p. ej. PREMIUM")
            String planCode) {
    }

    public record SubscriptionResponse(
            Long id,
            Long customerId,
            String planCode,
            int monthlyPriceCents,
            String status,
            Instant startedAt,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant cancelledAt,
            boolean cancelAtPeriodEnd) {

        public static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(s.getId(), s.getCustomer().getId(), s.getPlan().getCode(),
                    s.getPlan().getMonthlyPriceCents(), s.getStatus().name(), s.getStartedAt(),
                    s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getCancelledAt(),
                    s.isCancellationScheduled());
        }
    }

    public record PlanChangeResponse(SubscriptionResponse subscription, InvoiceResponse prorationInvoice) {

        public static PlanChangeResponse from(PlanChange change) {
            return new PlanChangeResponse(SubscriptionResponse.from(change.subscription()),
                    change.prorationInvoice() == null ? null : InvoiceResponse.from(change.prorationInvoice()));
        }
    }
}
