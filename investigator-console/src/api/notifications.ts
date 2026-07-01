import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { config } from "../config";
import type { Notification } from "../types";

/**
 * Live notification feed (Phase 5C/5E). Connects to notification-service through the gateway over
 * SockJS + STOMP and subscribes to /topic/alerts and /topic/cases. The gateway leaves the /ws handshake
 * open (a browser can't set Authorization on the WS upgrade), so no token is attached here — hardening
 * the handshake (token in the CONNECT frame) is a later step. Returns a disposer to tear the socket down.
 */
export function connectNotifications(
  onNotification: (n: Notification) => void,
): () => void {
  const client = new Client({
    // SockJS negotiates over http(s); the gateway routes /ws/** to notification-service.
    webSocketFactory: () => new SockJS(`${config.gatewayUrl}/ws`),
    reconnectDelay: 5000,
    onConnect: () => {
      const handle = (msg: IMessage) => {
        try {
          onNotification(JSON.parse(msg.body) as Notification);
        } catch {
          /* ignore malformed frame */
        }
      };
      client.subscribe("/topic/alerts", handle);
      client.subscribe("/topic/cases", handle);
    },
  });

  client.activate();
  return () => {
    client.deactivate();
  };
}
