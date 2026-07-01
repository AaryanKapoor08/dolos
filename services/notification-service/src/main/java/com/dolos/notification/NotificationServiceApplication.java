package com.dolos.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * notification-service (Phase 5C): bridges the platform's Kafka outcome events to the browser over
 * WebSocket/STOMP. Stateless — no Flyway, no JPA — so the app is just the web + messaging + Kafka
 * wiring configured in the {@code config} package.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
