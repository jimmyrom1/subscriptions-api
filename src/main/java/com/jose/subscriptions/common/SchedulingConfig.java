package com.jose.subscriptions.common;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * Tareas programadas con bloqueo en PostgreSQL (ShedLock).
 *
 * <p>La facturación ya era idempotente (UNIQUE por periodo y @Version), así que varias instancias
 * nunca podían facturar dos veces. El bloqueo evita además el trabajo repetido: sin él, todas
 * las instancias recorrerían las mismas suscripciones a las 02:00 y chocarían entre sí.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                // La hora la pone PostgreSQL: no importa si los relojes de las instancias difieren.
                .usingDbTime()
                .build());
    }
}
