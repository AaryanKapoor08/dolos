package com.dolos.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * bff-service (Phase 5D): the GraphQL Backend-for-Frontend. A reactive Spring Boot app whose GraphQL
 * resolvers aggregate the business services over a load-balanced WebClient. Stateless — no persistence.
 */
@SpringBootApplication
public class BffServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffServiceApplication.class, args);
    }
}
