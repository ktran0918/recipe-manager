package com.recipemanager.api.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

// Validates the ws_token clients use to open /ws/jobs/{jobId} — a separate mechanism from
// the JWT Bearer token every other endpoint uses, because browsers' native WebSocket API
// cannot set custom headers on the handshake request. A short-lived, single-purpose Redis
// key is the simplest way to prove "the holder of this token already authenticated via
// POST /recipes/parse" without reinventing JWT validation for a one-off connection.
// C# equivalent: issuing a short-lived SignalR connection token via a Redis-backed cache.
@Service
public class WsTokenService {

    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;

    public WsTokenService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String issueToken(UUID jobId) {
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(key(jobId), token, TTL);
        return token;
    }

    public boolean isValid(UUID jobId, String token) {
        if (token == null) {
            return false;
        }
        String stored = stringRedisTemplate.opsForValue().get(key(jobId));
        return stored != null && stored.equals(token);
    }

    private static String key(UUID jobId) {
        return "ws_token:" + jobId;
    }
}
