package com.jose.subscriptions.billing;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jose.subscriptions.billing.BillingService.RenewalOutcome;
import com.jose.subscriptions.subscription.SubscriptionRepository;
import com.jose.subscriptions.subscription.SubscriptionStatus;

/**
 * Proceso diario de renovación. Sin @Transactional a propósito: cada suscripción se renueva en
 * su propia transacción (dentro de BillingService) para que un fallo no deshaga las demás.
 */
@Component
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private final SubscriptionRepository subscriptions;
    private final BillingService billing;
    private final Clock clock;

    public BillingScheduler(SubscriptionRepository subscriptions, BillingService billing, Clock clock) {
        this.subscriptions = subscriptions;
        this.billing = billing;
        this.clock = clock;
    }

    @Scheduled(cron = "${billing.renewal-cron}")
    public void renewDueSubscriptions() {
        runOnce();
    }

    public RunSummary runOnce() {
        var due = subscriptions.findDueIds(SubscriptionStatus.ACTIVE, clock.instant());
        Map<RenewalOutcome, Integer> outcomes = new EnumMap<>(RenewalOutcome.class);
        int skipped = 0;
        int failed = 0;
        for (Long id : due) {
            try {
                outcomes.merge(billing.renew(id), 1, Integer::sum);
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
                // Otra ejecución (o otra instancia) ya la ha renovado: no es un error.
                skipped++;
                log.warn("Suscripción {} ya renovada por otra ejecución; se omite", id);
            } catch (RuntimeException e) {
                failed++;
                log.error("Error renovando la suscripción {}", id, e);
            }
        }
        var summary = new RunSummary(due.size(), outcomes.getOrDefault(RenewalOutcome.RENEWED, 0),
                outcomes.getOrDefault(RenewalOutcome.TERMINATED, 0), skipped, failed);
        log.info("Renovación diaria: {}", summary);
        return summary;
    }

    public record RunSummary(int due, int renewed, int terminated, int skipped, int failed) {
    }
}
