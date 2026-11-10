package com.recipemanager.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipemanager.api.config.WsTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// @EnableWebSocket + WebSocketConfigurer is the raw (non-STOMP) WebSocket registration API —
// the lowest-level option Spring offers, appropriate here since this endpoint only ever
// pushes one-directional status updates, not a general pub/sub messaging protocol.
// C# equivalent: app.UseWebSockets() plus a custom handler mapped to a route — the ASP.NET
// Core equivalent of skipping SignalR's higher-level abstraction.
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WsTokenService wsTokenService;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectMapper objectMapper;

    public WebSocketConfig(WsTokenService wsTokenService,
                           RedisConnectionFactory redisConnectionFactory,
                           ObjectMapper objectMapper) {
        this.wsTokenService = wsTokenService;
        this.redisConnectionFactory = redisConnectionFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(jobStatusWebSocketHandler(), "/ws/jobs/*")
                .addInterceptors(new WsTokenHandshakeInterceptor(wsTokenService));
    }

    @Bean
    public JobStatusWebSocketHandler jobStatusWebSocketHandler() {
        return new JobStatusWebSocketHandler(redisMessageListenerContainer(), objectMapper);
    }

    // RedisMessageListenerContainer manages its own thread pool for pub/sub dispatch — it
    // must be a singleton bean (not created per connection), or every new WebSocket
    // connection would spin up a redundant listener container and thread pool.
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        return container;
    }
}
