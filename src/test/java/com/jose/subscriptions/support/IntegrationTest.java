package com.jose.subscriptions.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de integración: contexto completo de Spring, MockMvc y PostgreSQL real.
 *
 * <p>Por defecto arranca PostgreSQL con Testcontainers (es lo que hace la CI). Si se define
 * TEST_DATABASE_URL se usa esa base de datos en su lugar, para poder ejecutar los tests en
 * una máquina sin Docker. En ambos casos el esquema lo crea Flyway con las migraciones reales.
 */
@SpringBootTest(properties = "billing.renewal-cron=-") // el cron no se dispara solo en los tests
@AutoConfigureMockMvc
@Import(IntegrationTest.TestClock.class)
public abstract class IntegrationTest {

    private static final String EXTERNAL_DB = System.getenv("TEST_DATABASE_URL");
    private static final PostgreSQLContainer POSTGRES;

    static {
        if (EXTERNAL_DB == null) {
            POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
            POSTGRES.start(); // un contenedor para toda la suite; Ryuk lo elimina al acabar
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB);
            registry.add("spring.datasource.username", () -> env("TEST_DATABASE_USER", "app"));
            registry.add("spring.datasource.password", () -> env("TEST_DATABASE_PASSWORD", "app"));
        }
    }

    private static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null ? fallback : value;
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE invoices, subscriptions, customers RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE plans SET active = TRUE");
        clock.reset();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
