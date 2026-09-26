package com.jose.subscriptions.subscription;

public enum SubscriptionStatus {
    /** En vigor. Si tiene cancelledAt, terminará al final del periodo actual. */
    ACTIVE,
    /** Terminada. Ya no se renueva ni se factura. */
    CANCELLED
}
