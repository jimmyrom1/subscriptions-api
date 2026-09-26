package com.jose.subscriptions.common;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El tiempo se inyecta como un bean: los tests pueden "adelantar el reloj" para probar
 * renovaciones y prorrateos sin esperar un mes ni usar Instant.now() a escondidas.
 */
@Configuration
public class ClockConfig {

    /**
     * Truncado a microsegundos, la precisión de TIMESTAMPTZ en PostgreSQL. Si no, un instante
     * recién creado (nanosegundos) y el mismo instante leído de la base de datos no coinciden.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }
}
