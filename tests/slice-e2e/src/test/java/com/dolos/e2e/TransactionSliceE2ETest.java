package com.dolos.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.dolos.alert.AlertServiceApplication;
import com.dolos.ingestion.IngestionServiceApplication;
import com.dolos.scoring.ScoringServiceApplication;
import com.dolos.transaction.TransactionServiceApplication;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Phase 1F end-to-end test of the Phase 1 vertical slice against real infrastructure.
 *
 * <p>Spins up real Postgres + Redpanda with Testcontainers, then boots the four slice services
 * (ingestion → transaction + scoring → alert) in-process, each wired to that infra. A single
 * high-amount transaction is POSTed to ingestion; the test asserts the whole chain runs by waiting
 * for the resulting alert to surface at alert-service's {@code GET /api/alerts}:
 *
 * <pre>{@code
 * POST /ingest/transactions  ->  TransactionReceived  ->  RiskScored (score 60)  ->  Alert + AlertRaised
 * }</pre>
 *
 * <h2>Why the unusual wiring</h2>
 * All four services share one JVM and therefore one merged classpath, which collides on three
 * things; each is neutralised deterministically so the contexts stay isolated:
 * <ul>
 *   <li><b>{@code db/migration}</b> — the three V1 migrations merge into one folder, so Flyway is
 *       disabled in every context and the schemas/tables are created up-front from the same SQL.</li>
 *   <li><b>{@code application.yml}</b> — every service ships one at the classpath root, so file-based
 *       config is switched off ({@code spring.config.location=optional:classpath:/__e2e_none__/})
 *       and all config is passed as high-precedence command-line args.</li>
 *   <li><b>auto-configuration</b> — JPA/JDBC/R2DBC are on the classpath for every context, so each
 *       context excludes the DB auto-configs it must not run (e.g. ingestion is reactive-only).</li>
 * </ul>
 *
 * <p>The infrastructure containers are started manually after a Docker-availability assumption, so
 * on a machine where Testcontainers cannot reach a Docker daemon the whole class is skipped rather
 * than failing the build; CI (Linux Docker) runs it for real.
 */
class TransactionSliceE2ETest {

    // Auto-configuration classes excluded per context to keep the merged classpath from configuring
    // a datastore a given service does not use.
    private static final String JDBC =
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration";
    private static final String JPA =
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration";
    private static final String R2DBC =
            "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.r2dbc.R2dbcDataAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcRepositoriesAutoConfiguration";
    private static final String FLYWAY =
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";

    // Phase 5B secured every service, but this in-process slice boots them WITHOUT Keycloak (file config
    // is disabled below, so no jwk-set-uri) and calls them unauthenticated. Disable the whole security
    // stack per web type so the resource-server chains (which would 401 the calls, or fail to start with
    // no JwtDecoder) never install. Production wiring is Docker-verified with real tokens through the
    // gateway.
    private static final String SECURITY_SERVLET =
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration,"
                    // Actuator's management security chain needs an HttpSecurity bean, which the excluded
                    // SecurityAutoConfiguration would have provided — exclude it too, or it fails to wire.
                    + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration,"
                    + "com.dolos.security.DolosSecurityAutoConfiguration";
    private static final String SECURITY_REACTIVE =
            "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration,"
                    + "org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration,"
                    + "com.dolos.security.DolosReactiveSecurityAutoConfiguration";

    private static final String NO_CONFIG = "optional:classpath:/__e2e_none__/";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:pg16")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("dolos")
                    .withUsername("dolos")
                    .withPassword("dolos");

    static final RedpandaContainer REDPANDA =
            new RedpandaContainer(DockerImageName.parse("redpandadata/redpanda:v24.3.1"));

    private static boolean infraStarted;

    private static ConfigurableApplicationContext scoring;
    private static ConfigurableApplicationContext transaction;
    private static ConfigurableApplicationContext alert;
    private static ConfigurableApplicationContext ingestion;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static String ingestionBase;
    private static String alertBase;

    @BeforeAll
    static void startSlice() throws Exception {
        assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not reachable by Testcontainers; skipping the slice e2e test");
        POSTGRES.start();
        REDPANDA.start();
        infraStarted = true;

        prepareSchemas();

        String bootstrap = stripScheme(REDPANDA.getBootstrapServers());
        String jdbcUrl = POSTGRES.getJdbcUrl();
        String user = POSTGRES.getUsername();
        String pass = POSTGRES.getPassword();
        String r2dbcUrl =
                "r2dbc:postgresql://"
                        + POSTGRES.getHost()
                        + ":"
                        + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                        + "/"
                        + POSTGRES.getDatabaseName()
                        + "?schema=ingestion";

        // Scoring: pure consume → score → publish, no datastore. Its Kafka Streams topology needs an
        // application-id; with file config disabled the usual `spring.application.name` fallback is gone,
        // so set it explicitly (otherwise defaultKafkaStreamsConfig fails: "application-id is mandatory").
        scoring =
                boot(
                        ScoringServiceApplication.class,
                        WebApplicationType.NONE,
                        exclude(JDBC, JPA, R2DBC, FLYWAY),
                        "--spring.kafka.bootstrap-servers=" + bootstrap,
                        "--spring.kafka.streams.application-id=scoring-e2e",
                        "--spring.kafka.consumer.group-id=scoring-e2e");

        // Transaction: canonical store (JPA, public schema).
        transaction =
                boot(
                        TransactionServiceApplication.class,
                        WebApplicationType.NONE,
                        exclude(R2DBC, FLYWAY),
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + user,
                        "--spring.datasource.password=" + pass,
                        "--spring.jpa.hibernate.ddl-auto=none",
                        "--spring.kafka.bootstrap-servers=" + bootstrap,
                        "--spring.kafka.consumer.group-id=transaction-e2e");

        // Alert: JPA in its own `alert` schema; exposes GET /api/alerts.
        alert =
                boot(
                        AlertServiceApplication.class,
                        WebApplicationType.SERVLET,
                        exclude(R2DBC, FLYWAY, SECURITY_SERVLET),
                        "--server.port=0",
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + user,
                        "--spring.datasource.password=" + pass,
                        "--spring.jpa.hibernate.ddl-auto=none",
                        "--spring.jpa.properties.hibernate.default_schema=alert",
                        "--dolos.alert.score-threshold=60",
                        "--spring.kafka.bootstrap-servers=" + bootstrap,
                        "--spring.kafka.consumer.group-id=alert-e2e");

        // Ingestion: reactive edge (R2DBC in the `ingestion` schema); accepts POST /ingest/transactions.
        ingestion =
                boot(
                        IngestionServiceApplication.class,
                        WebApplicationType.REACTIVE,
                        exclude(JDBC, JPA, FLYWAY, SECURITY_REACTIVE),
                        "--server.port=0",
                        "--spring.r2dbc.url=" + r2dbcUrl,
                        "--spring.r2dbc.username=" + user,
                        "--spring.r2dbc.password=" + pass,
                        "--spring.kafka.bootstrap-servers=" + bootstrap);

        ingestionBase = "http://localhost:" + port(ingestion);
        alertBase = "http://localhost:" + port(alert);
    }

    @AfterAll
    static void stopSlice() {
        for (ConfigurableApplicationContext ctx :
                new ConfigurableApplicationContext[] {ingestion, alert, transaction, scoring}) {
            if (ctx != null) {
                ctx.close();
            }
        }
        if (infraStarted) {
            REDPANDA.stop();
            POSTGRES.stop();
        }
    }

    @Test
    void highAmountTransaction_flowsThroughSlice_toAnAlert() throws Exception {
        String account = "ACC-E2E-" + System.nanoTime();
        String body =
                """
                {"accountId":"%s","counterpartyAccountId":"ACC-CP","amount":15000.00,\
                "currency":"CAD","direction":"DEBIT","description":"slice e2e",\
                "occurredAt":"2026-06-21T12:00:00Z"}"""
                        .formatted(account);

        HttpResponse<String> ingestResponse =
                HTTP.send(
                        HttpRequest.newBuilder(URI.create(ingestionBase + "/ingest/transactions"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(ingestResponse.statusCode()).isEqualTo(202);

        String transactionId = jsonField(ingestResponse.body(), "transactionId");
        assertThat(transactionId).isNotBlank();

        // The alert is produced asynchronously after the event crosses ingestion → scoring → alert.
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(
                        () -> {
                            HttpResponse<String> alerts =
                                    HTTP.send(
                                            HttpRequest.newBuilder(
                                                            URI.create(alertBase + "/api/alerts?size=100"))
                                                    .GET()
                                                    .build(),
                                            HttpResponse.BodyHandlers.ofString());
                            assertThat(alerts.statusCode()).isEqualTo(200);
                            assertThat(alerts.body()).contains(transactionId);
                            assertThat(alerts.body()).contains(account);
                            assertThat(alerts.body()).contains("\"score\":60");
                        });
    }

    /**
     * Creates the service schemas + tables up-front (Flyway is disabled in every context), applying
     * each service's full migration chain in version order so the e2e schema matches production —
     * including the alert {@code detail} (V2), the ring/dedupe columns (V3), and the CQRS
     * {@code alert_view} read model (V4) the queue is served from.
     */
    private static void prepareSchemas() throws Exception {
        try (Connection con = POSTGRES.createConnection("")) {
            runMigrations(
                    con,
                    "ingestion",
                    "/db/migration/V1__raw_transactions.sql",
                    "/db/migration/V2__raw_transactions_country.sql",
                    "/db/migration/V3__raw_transactions_customer_device.sql");
            runMigrations(con, "public", "/db/migration/V1__transactions.sql");
            runMigrations(
                    con,
                    "alert",
                    "/db/migration/V1__alerts.sql",
                    "/db/migration/V2__alerts_detail.sql",
                    "/db/migration/V3__alerts_ring.sql",
                    "/db/migration/V4__alert_view.sql");
        }
    }

    private static void runMigrations(Connection con, String schema, String... resources)
            throws Exception {
        try (Statement st = con.createStatement()) {
            if (!"public".equals(schema)) {
                st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            }
            st.execute("SET search_path TO " + schema);
            for (String resource : resources) {
                st.execute(readResource(resource));
            }
        }
    }

    /** Extracts a string JSON field value without pulling in a JSON parser dependency. */
    private static String jsonField(String body, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = TransactionSliceE2ETest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Migration resource not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ConfigurableApplicationContext boot(
            Class<?> app, WebApplicationType web, String... args) {
        String[] full = new String[args.length + 3];
        // File config is switched off (below), which drops each service's `optional:configserver` import.
        // spring-cloud-config-client is still on the classpath, and its import-check would then abort
        // startup ("No spring.config.import ... configserver") — so disable the client + its import-check;
        // this in-process slice provides all config via the command-line args, never a config server.
        full[0] = "--spring.config.location=" + NO_CONFIG;
        full[1] = "--spring.cloud.config.enabled=false";
        full[2] = "--spring.cloud.config.import-check.enabled=false";
        System.arraycopy(args, 0, full, 3, args.length);
        return new SpringApplicationBuilder(app).web(web).bannerMode(Banner.Mode.OFF).run(full);
    }

    private static String exclude(String... autoConfigs) {
        return "--spring.autoconfigure.exclude=" + String.join(",", autoConfigs);
    }

    private static int port(ConfigurableApplicationContext ctx) {
        return Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port", "0"));
    }

    private static String stripScheme(String bootstrap) {
        int idx = bootstrap.indexOf("://");
        return idx < 0 ? bootstrap : bootstrap.substring(idx + 3);
    }
}
