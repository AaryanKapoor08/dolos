package com.dolos.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket wiring (Phase 5C). Browsers open a WebSocket at {@code /ws} and subscribe to
 * destinations under {@code /topic} (e.g. {@code /topic/alerts}, {@code /topic/cases}); the Kafka
 * listeners push frames there via {@link org.springframework.messaging.simp.SimpMessagingTemplate}.
 *
 * <p>Two endpoint registrations share the {@code /ws} path: a raw WebSocket endpoint (used by tests and
 * any non-browser STOMP client) and a SockJS fallback (used by the React console, which connects with
 * SockJS + @stomp/stompjs). {@code setAllowedOriginPatterns("*")} keeps local dev simple; the browser
 * still can't set an Authorization header on the upgrade, so the handshake is left open here and the
 * gateway permits {@code /ws/**} — auth on the live feed is a later hardening step (token in the CONNECT
 * frame). An in-memory {@link MessageBrokerRegistry#enableSimpleBroker simple broker} is enough for a
 * fan-out feed; no external STOMP broker is needed.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Server -> client fan-out destinations live under /topic; the app never receives client sends.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Raw WebSocket at /ws (STOMP clients / tests).
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        // SockJS fallback at /ws (the browser console) — served under /ws/** (info, xhr_streaming, ...).
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
