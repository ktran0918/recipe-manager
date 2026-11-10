package com.recipemanager.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Bridges Redis pub/sub (where the scraper publishes job status updates) to this specific
// client's WebSocket connection. One MessageListener is registered per connected session,
// subscribed to that session's own job.status.{jobId} channel — not a single shared listener
// for every job, since each client should only ever see updates for the job it asked about.
// C# equivalent: a SignalR Hub whose OnConnectedAsync subscribes to a Redis channel (via
// StackExchange.Redis's ISubscriber) and forwards messages to Clients.Caller.SendAsync(...).
public class JobStatusWebSocketHandler extends TextWebSocketHandler {

    private static final Set<String> TERMINAL_STATUSES = Set.of("complete", "failed");

    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    // Tracks the listener registered for each session so it can be torn down when the
    // connection closes. RedisMessageListenerContainer has no "unsubscribe by session" API —
    // only removeMessageListener(listener, topic) — so the listener instance itself has to
    // be kept around to remove it later.
    private final Map<String, MessageListener> listenersBySessionId = new ConcurrentHashMap<>();

    public JobStatusWebSocketHandler(RedisMessageListenerContainer listenerContainer, ObjectMapper objectMapper) {
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        UUID jobId = (UUID) session.getAttributes().get("jobId");
        ChannelTopic topic = new ChannelTopic("job.status." + jobId);

        MessageListener listener = (message, pattern) -> handleRedisMessage(session, message);
        listenersBySessionId.put(session.getId(), listener);
        listenerContainer.addMessageListener(listener, topic);
    }

    private void handleRedisMessage(WebSocketSession session, Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            session.sendMessage(new TextMessage(body));
        } catch (IOException e) {
            // The client may have disconnected between the publish and this delivery attempt —
            // nothing more to do than skip this message; afterConnectionClosed handles cleanup.
            return;
        }

        if (isTerminalStatus(body)) {
            unsubscribe(session.getId());
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                // Already closing/closed — nothing actionable.
            }
        }
    }

    private boolean isTerminalStatus(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return TERMINAL_STATUSES.contains(node.path("status").asText());
        } catch (IOException e) {
            return false;
        }
    }

    // Unsubscribes the listener for this session if the client disconnects early — e.g.
    // closes the browser tab before the job reaches a terminal status. Without this, the
    // listener keeps running forever, leaking a subscription for a connection nobody is
    // listening on anymore.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unsubscribe(session.getId());
    }

    private void unsubscribe(String sessionId) {
        MessageListener listener = listenersBySessionId.remove(sessionId);
        if (listener != null) {
            listenerContainer.removeMessageListener(listener);
        }
    }
}
