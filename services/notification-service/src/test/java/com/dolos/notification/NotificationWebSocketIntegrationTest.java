package com.dolos.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.dolos.events.AlertRaised;
import com.dolos.notification.api.Notification;
import com.dolos.notification.messaging.NotificationListeners;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * DoD for Phase 5C: a STOMP client subscribed to {@code /topic/alerts} receives a live message when a
 * new alert is raised. Rather than stand up Kafka, this boots the app, connects a real STOMP client
 * over the WebSocket endpoint, subscribes, and drives the {@link NotificationListeners} handler with an
 * {@link AlertRaised} JSON payload exactly as the Kafka listener would — proving the parse → push →
 * broker → subscriber path end to end. Eureka/config/Kafka startup are disabled so the test is
 * hermetic; the {@code /ws} handshake is open per {@code NotificationSecurityConfig}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "eureka.client.enabled=false",
            "spring.cloud.config.enabled=false",
            "spring.config.import=",
            "spring.kafka.listener.auto-startup=false"
        })
class NotificationWebSocketIntegrationTest {

    @LocalServerPort int port;

    @Autowired NotificationListeners listeners;

    @Test
    void alertRaisedIsPushedToSubscribers() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

        WebSocketStompClient stompClient =
                new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        stompClient.setMessageConverter(converter);

        BlockingQueue<Notification> received = new LinkedBlockingQueue<>();

        StompSession session =
                stompClient
                        .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS);

        session.subscribe(
                "/topic/alerts",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Notification.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        received.add((Notification) payload);
                    }
                });

        // Let the SUBSCRIBE register on the broker before we publish.
        Thread.sleep(300);

        UUID alertId = UUID.randomUUID();
        AlertRaised event =
                new AlertRaised(
                        alertId, UUID.randomUUID(), "acc-123", 88, List.of("LARGE_AMOUNT"), Instant.now());
        listeners.onAlertRaised(mapper.writeValueAsString(event));

        Notification frame = received.poll(5, TimeUnit.SECONDS);
        assertThat(frame).isNotNull();
        assertThat(frame.kind()).isEqualTo("ALERT_RAISED");
        assertThat(frame.entityId()).isEqualTo(alertId.toString());
        assertThat(frame.accountId()).isEqualTo("acc-123");
    }
}
