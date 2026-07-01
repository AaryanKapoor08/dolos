package com.dolos.transaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.transaction.domain.Direction;
import com.dolos.transaction.domain.TransactionEntity;
import com.dolos.transaction.repo.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Phase 6E persistence integration test: proves transaction-service's Flyway migrations apply cleanly
 * to a REAL Postgres (the same {@code pgvector/pgvector:pg16} image compose uses) and that the JPA
 * repository round-trips against the migrated schema — a focused, per-service complement to the
 * end-to-end {@code tests:slice-e2e}.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} is the declarative form of the house
 * {@code assumeTrue(isDockerAvailable())} guard: the class is SKIPPED where Testcontainers can't reach a
 * Docker daemon (locally on this box) and runs for real on CI (Linux Docker).
 */
@DataJpaTest(properties = "spring.cloud.config.enabled=false")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class TransactionPersistenceIT {

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

    @Autowired private TransactionRepository repository;

    @Test
    void flywayMigratesTheSchemaAndTheRepositoryRoundTrips() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        repository.save(
                new TransactionEntity(
                        id,
                        "ACC-IT-1",
                        "ACC-CP",
                        new BigDecimal("15000.0000"),
                        "USD",
                        Direction.DEBIT,
                        "persistence IT",
                        now,
                        now));

        assertThat(repository.findById(id)).isPresent();
        assertThat(
                        repository.findByAccountIdOrderByOccurredAtDesc(
                                "ACC-IT-1", PageRequest.of(0, 10)))
                .extracting(TransactionEntity::getId)
                .containsExactly(id);
    }
}
