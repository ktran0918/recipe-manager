package com.recipemanager.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Bridges Redis pub/sub (where the scraper publishes job status updates) to this specific
// client's WebSocket connection. One MessageListener is registered per connected session,
// subscribed to that session's own job.status.{jobId} channel — not a single shared listener
// for every job, since each client should only ever see updates for the job it asked about.
// C# equivalent: a SignalR Hub whose OnConnectedAsync subscribes to a Redis channel (via
// StackExchange.Redis's ISubscriber) and forwards messages to Clients.Caller.SendAsync(...).
public class JobStatusWebSocketHandler extends TextWebSocketHandler {

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

    // TODO: Subscribe to this job's Redis channel and forward every message to the client.
    //
    // 1. Read the jobId stashed by WsTokenHandshakeInterceptor:
    //      UUID jobId = (UUID) session.getAttributes().get("jobId");
    //
    // 2. Build a MessageListener — a functional interface with one method,
    //    onMessage(Message message, byte[] pattern) — that:
    //      - forwards the raw payload to the client:
    //          session.sendMessage(new TextMessage(new String(message.getBody())));
    //      - parses it (e.g. via objectMapper.readTree(message.getBody())) to check the
    //        "status" field — if it's "complete" or "failed", close the session
    //        (session.close()) and call listenerContainer.removeMessageListener(listener)
    //        so this subscription doesn't keep running after the job is done.
    //
    // 3. Register it on the shared container:
    //      listenerContainer.addMessageListener(listener, new ChannelTopic("job.status." + jobId));
    //    (ChannelTopic is org.springframework.data.redis.listener.ChannelTopic — a plain
    //    pub/sub channel subscription, not a pattern match.)
    //
    // 4. Store the listener in listenersBySessionId keyed by session.getId(), so
    //    afterConnectionClosed can clean it up if the client disconnects before a terminal
    //    status arrives.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        // intentionally left for the TODO above
    }

    // TODO: Unsubscribe the listener for this session if the client disconnects early —
    // e.g. closes the browser tab before the job reaches a terminal status. Look up
    // listenersBySessionId.remove(session.getId()) and, if present, call
    // listenerContainer.removeMessageListener(listener). Skipping this leaks a live Redis
    // subscription for a connection nobody is listening on anymore.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // intentionally left for the TODO above
    }
}
