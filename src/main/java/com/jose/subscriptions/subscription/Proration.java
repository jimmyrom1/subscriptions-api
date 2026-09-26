package com.jose.subscriptions.subscription;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Cálculo del prorrateo al cambiar de plan a mitad de periodo.
 *
 * <p>Se cobra (o se abona) la diferencia de precio por la fracción de periodo que queda.
 * La fracción se calcula por segundos, no por días enteros, para que el resultado no dependa
 * de la hora a la que se hace el cambio. El redondeo a céntimos es HALF_UP y se hace una sola
 * vez, al final.
 */
public final class Proration {

    private Proration() {
    }

    /**
     * @return importe en céntimos; negativo si es un downgrade (abono al cliente)
     */
    public static int amountCents(int oldPriceCents, int newPriceCents,
                                  Instant periodStart, Instant periodEnd, Instant changeAt) {
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("El periodo debe tener duración positiva");
        }
        if (changeAt.isBefore(periodStart) || changeAt.isAfter(periodEnd)) {
            throw new IllegalArgumentException("El cambio debe caer dentro del periodo");
        }
        long total = Duration.between(periodStart, periodEnd).toSeconds();
        long remaining = Duration.between(changeAt, periodEnd).toSeconds();
        return BigDecimal.valueOf((long) newPriceCents - oldPriceCents)
                .multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
