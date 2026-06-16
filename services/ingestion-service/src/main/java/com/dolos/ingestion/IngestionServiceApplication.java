package com.dolos.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Entry point for ingestion-service — the reactive edge that turns inbound HTTP transactions
 * into {@code TransactionReceived} events on Kafka, after recording each one in a raw-ingest
 * table via R2DBC.
 *
 * <p>The JDBC {@link DataSourceAutoConfiguration} is excluded: the application's data path is
 * fully reactive (R2DBC), and Flyway runs migrations over its own short-lived JDBC connection
 * configured via {@code spring.flyway.url} (so no application-wide JDBC DataSource is needed).
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class IngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionServiceApplication.class, args);
    }
}
