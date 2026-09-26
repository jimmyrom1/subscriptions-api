package com.jose.subscriptions.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProrationTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-31T00:00:00Z"); // 30 días

    private static Instant daysIn(double days) {
        return START.plus(Duration.ofSeconds((long) (days * 86_400)));
    }

    @ParameterizedTest(name = "{0} → {1} con {2} días gastados = {3} cts")
    @CsvSource({
            "990,  1690, 15, 350",   // upgrade a mitad de periodo
            "1690, 990,  15, -350",  // downgrade: abono
            "0,    990,  0,  990",   // al principio: el periodo completo
            "990,  1690, 30, 0",     // al final: nada
            "0,    1000, 20, 333",   // 1000 × 10/30 = 333,33 → 333
            "0,    1000, 10, 667",   // 1000 × 20/30 = 666,67 → 667
    })
    void chargesThePriceDifferenceForTheRemainingFraction(int oldPrice, int newPrice, double spent, int expected) {
        assertThat(Proration.amountCents(oldPrice, newPrice, START, END, daysIn(spent))).isEqualTo(expected);
    }

    @Test
    void roundsHalfUp() {
        // 1 cto × 1/2 = 0,5 → 1
        assertThat(Proration.amountCents(0, 1, START, END, daysIn(15))).isEqualTo(1);
        assertThat(Proration.amountCents(1, 0, START, END, daysIn(15))).isEqualTo(-1);
    }

    @Test
    void usesSecondsNotWholeDays() {
        // Medio día más tarde que la mitad: la fracción ya no es exactamente 1/2.
        assertThat(Proration.amountCents(990, 1690, START, END, daysIn(15.5))).isEqualTo(338);
    }

    @Test
    void rejectsChangesOutsideThePeriod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Proration.amountCents(0, 990, START, END, END.plusSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Proration.amountCents(0, 990, END, START, START));
    }
}
