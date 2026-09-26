package com.jose.subscriptions.billing;

public enum InvoiceKind {
    /** Primer periodo, al darse de alta. */
    INITIAL,
    /** Periodo siguiente, emitida por el proceso de renovación. */
    RENEWAL,
    /** Diferencia por cambio de plan a mitad de periodo (negativa si es un abono). */
    PRORATION
}
