package com.jose.subscriptions.billing;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jose.subscriptions.customer.CustomerRepository;
import com.jose.subscriptions.customer.CustomerService;
import com.jose.subscriptions.subscription.Subscription;
import com.jose.subscriptions.subscription.SubscriptionRepository;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final InvoiceRepository invoices;
    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final Clock clock;

    public BillingService(InvoiceRepository invoices, SubscriptionRepository subscriptions,
                          CustomerRepository customers, Clock clock) {
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.clock = clock;
    }

    @Transactional
    public Invoice issueInitialInvoice(Subscription s) {
        return invoices.save(new Invoice(s, InvoiceKind.INITIAL, s.getPlan().getMonthlyPriceCents(),
                s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), clock.instant()));
    }

    @Transactional
    public Invoice issueProrationInvoice(Subscription s, int amountCents, Instant changeAt) {
        return invoices.save(new Invoice(s, InvoiceKind.PRORATION, amountCents,
                changeAt, s.getCurrentPeriodEnd(), clock.instant()));
    }

    /**
     * Renueva una suscripción vencida: emite la factura del periodo siguiente y lo avanza,
     * todo en la misma transacción. Si la suscripción tenía la cancelación programada,
     * termina en lugar de renovarse.
     *
     * <p>Es idempotente: si dos ejecuciones compiten por la misma suscripción, la segunda
     * choca con UNIQUE (subscription_id, period_start) o con el @Version y hace rollback
     * completo. Nunca se queda una factura sin avanzar el periodo ni al revés.
     */
    @Transactional
    public RenewalOutcome renew(long subscriptionId) {
        var s = subscriptions.findWithPlanById(subscriptionId).orElse(null);
        var now = clock.instant();
        if (s == null || !s.isDue(now)) {
            return RenewalOutcome.NOT_DUE;
        }
        if (s.isCancellationScheduled()) {
            s.terminate();
            log.info("Suscripción {} terminada al final de su periodo", s.getId());
            return RenewalOutcome.TERMINATED;
        }
        // Bucle por si el proceso estuvo parado varios días y hay más de un periodo pendiente.
        int periods = 0;
        while (s.isDue(now)) {
            s.advancePeriod();
            invoices.saveAndFlush(new Invoice(s, InvoiceKind.RENEWAL, s.getPlan().getMonthlyPriceCents(),
                    s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), now));
            periods++;
        }
        subscriptions.flush();
        log.info("Suscripción {} renovada ({} periodo/s), nuevo fin {}", s.getId(), periods, s.getCurrentPeriodEnd());
        return RenewalOutcome.RENEWED;
    }

    @Transactional(readOnly = true)
    public List<Invoice> invoicesFor(long customerId) {
        if (!customers.existsById(customerId)) {
            throw CustomerService.notFound(customerId);
        }
        return invoices.findByCustomerId(customerId);
    }

    public enum RenewalOutcome {
        RENEWED, TERMINATED, NOT_DUE
    }
}
