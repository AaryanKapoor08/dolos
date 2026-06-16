package com.dolos.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for transaction-service — Dolos's canonical store of record for transactions.
 *
 * <p>Phase 0C wires up the Spring Boot baseline (Actuator, virtual threads, JSON logging);
 * persistence (JPA + Flyway + REST) arrives in Phase 0D.
 */
@SpringBootApplication
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
