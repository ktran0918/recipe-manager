package com.recipemanager.api.ws;

import com.recipemanager.api.config.WsTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

// Runs once, before the HTTP connection upgrades to a WebSocket. This is the right place to
// reject an invalid/missing ws_token — rejecting here sends a normal HTTP error status;
// rejecting inside the handler itself would mean completing the upgrade first and then
// closing the socket, a worse experience for the client.
// C# equivalent: a check before ASP.NET Core's WebSocket middleware accepts the connection,
// or validation inside a SignalR Hub's OnConnectedAsync that aborts the connection.
public class WsTokenHandshakeInterceptor implements HandshakeInterceptor {

    private final WsTokenService wsTokenService;

    public WsTokenHandshakeInterceptor(WsTokenService wsTokenService) {
        this.wsTokenService = wsTokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // WebSocketConfig registers this handler at the wildcard pattern "/ws/jobs/*", not a
        // URI template like "/ws/jobs/{jobId}", so there's no @PathVariable-style binding
        // here — the job id has to be pulled out of the raw request path instead.
        String path = request.getURI().getPath();
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");

        UUID jobId;
        try {
            jobId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        } catch (IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        if (!wsTokenService.isValid(jobId, token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Data stashed here survives into the WebSocket session — JobStatusWebSocketHandler
        // reads it back via session.getAttributes().
        attributes.put("jobId", jobId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No-op — nothing to clean up after a completed handshake.
    }
}
