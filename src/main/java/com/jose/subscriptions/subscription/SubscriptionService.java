package com.jose.subscriptions.subscription;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jose.subscriptions.billing.BillingService;
import com.jose.subscriptions.billing.Invoice;
import com.jose.subscriptions.common.BusinessException;
import com.jose.subscriptions.customer.CustomerRepository;
import com.jose.subscriptions.customer.CustomerService;
import com.jose.subscriptions.plan.Plan;
import com.jose.subscriptions.plan.PlanRepository;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final PlanRepository plans;
    private final BillingService billing;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptions, CustomerRepository customers,
                               PlanRepository plans, BillingService billing, Clock clock) {
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.plans = plans;
        this.billing = billing;
        this.clock = clock;
    }

    /** Alta: crea la suscripción y emite la factura del primer periodo. */
    @Transactional
    public Subscription subscribe(long customerId, String planCode) {
        var customer = customers.findById(customerId).orElseThrow(() -> CustomerService.notFound(customerId));
        var plan = activePlan(planCode);
        if (subscriptions.existsByCustomerIdAndStatus(customerId, SubscriptionStatus.ACTIVE)) {
            throw new BusinessException.Conflict("ACTIVE_SUBSCRIPTION_EXISTS",
                    "El cliente ya tiene una suscripción activa");
        }
        // saveAndFlush: si otra petición se cuela entre la comprobación y el INSERT, el índice
        // parcial one_active_sub_per_customer lo rechaza aquí mismo (→ 409).
        var subscription = subscriptions.saveAndFlush(Subscription.start(customer, plan, clock.instant()));
        billing.issueInitialInvoice(subscription);
        return subscription;
    }

    /**
     * Cambio de plan inmediato. Se factura (o abona) la diferencia por la parte del periodo
     * que queda; la siguiente renovación ya se cobra al precio del plan nuevo.
     */
    @Transactional
    public PlanChange changePlan(long subscriptionId, String planCode) {
        var subscription = get(subscriptionId);
        var now = clock.instant();
        requireModifiable(subscription, now);

        var newPlan = activePlan(planCode);
        var oldPlan = subscription.getPlan();
        if (newPlan.getId().equals(oldPlan.getId())) {
            throw new BusinessException.RuleViolation("SAME_PLAN", "La suscripción ya está en el plan " + planCode);
        }

        int amount = Proration.amountCents(oldPlan.getMonthlyPriceCents(), newPlan.getMonthlyPriceCents(),
                subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd(), now);
        subscription.changePlan(newPlan);
        Invoice invoice = amount == 0 ? null : billing.issueProrationInvoice(subscription, amount, now);
        return new PlanChange(subscription, invoice);
    }

    /** Cancela al final del periodo: el cliente conserva lo que ya ha pagado. */
    @Transactional
    public Subscription cancel(long subscriptionId) {
        var subscription = get(subscriptionId);
        requireModifiable(subscription, clock.instant());
        subscription.scheduleCancellation(clock.instant());
        return subscription;
    }

    @Transactional(readOnly = true)
    public Subscription get(long subscriptionId) {
        return subscriptions.findWithPlanById(subscriptionId)
                .orElseThrow(() -> new BusinessException.NotFound("SUBSCRIPTION_NOT_FOUND",
                        "No existe la suscripción " + subscriptionId));
    }

    @Transactional(readOnly = true)
    public Subscription activeFor(long customerId) {
        if (!customers.existsById(customerId)) {
            throw CustomerService.notFound(customerId);
        }
        return subscriptions.findFirstByCustomerIdAndStatus(customerId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException.NotFound("NO_ACTIVE_SUBSCRIPTION",
                        "El cliente no tiene ninguna suscripción activa"));
    }

    private void requireModifiable(Subscription subscription, Instant now) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessException.Conflict("SUBSCRIPTION_NOT_ACTIVE", "La suscripción ya no está activa");
        }
        if (subscription.isCancellationScheduled()) {
            throw new BusinessException.Conflict("CANCELLATION_SCHEDULED",
                    "La suscripción ya está cancelada y terminará el " + subscription.getCurrentPeriodEnd());
        }
        if (subscription.isDue(now)) {
            throw new BusinessException.Conflict("RENEWAL_PENDING",
                    "La suscripción está pendiente de renovación; inténtalo en unos minutos");
        }
    }

    private Plan activePlan(String code) {
        var plan = plans.findByCode(code)
                .orElseThrow(() -> new BusinessException.NotFound("PLAN_NOT_FOUND", "No existe el plan " + code));
        if (!plan.isActive()) {
            throw new BusinessException.RuleViolation("PLAN_INACTIVE", "El plan " + code + " ya no está disponible");
        }
        return plan;
    }

    public record PlanChange(Subscription subscription, Invoice prorationInvoice) {
    }
}
