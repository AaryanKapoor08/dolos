package com.dolos.alert.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.alert.domain.AlertEntity;
import com.dolos.alert.repo.AlertRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Phase 6E persistence integration test: proves alert-service's full Flyway chain (V1–V4, including the
 * {@code alert} schema and the CQRS {@code alert_view} read model) applies to a REAL Postgres and that
 * the JPA repository — with its unified {@code dedupeKey} idempotency invariant — round-trips against
 * the migrated schema. A focused, per-service complement to the end-to-end {@code tests:slice-e2e}.
 *
 * <p>The {@code alert} schema qualification comes from the service's own {@code application.yml}
 * (Flyway {@code schemas}/{@code default-schema} + Hibernate {@code default_schema}); only the datasource
 * is redirected to the container. {@code @Testcontainers(disabledWithoutDocker = true)} skips locally
 * (Docker unreachable) and runs on CI — the declarative form of the house {@code assumeTrue} guard.
 */
@DataJpaTest(properties = "spring.cloud.config.enabled=false")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AlertPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:pg16")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("dolos")
                    .withUsername("dolos")
                    .withPassword("dolos");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired private AlertRepository repository;

    @Test
    void flywayMigratesTheAlertSchemaAndTheRepositoryRoundTrips() {
        UUID transactionId = UUID.randomUUID();
        repository.save(
                AlertEntity.forTransaction(
                        UUID.randomUUID(),
                        transactionId,
                        "ACC-IT-2",
                        100,
                        List.of("LARGE_AMOUNT: amount 15000 is at/above the $10k reporting threshold"),
                        "score detail",
                        Instant.now()));

        // The unified idempotency key is the transaction id — the guard that stops a redelivery double-alerting.
        assertThat(repository.existsByTransactionId(transactionId)).isTrue();
        assertThat(repository.existsByDedupeKey(transactionId.toString())).isTrue();
        assertThat(repository.existsByTransactionId(UUID.randomUUID())).isFalse();
    }
}
