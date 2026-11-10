package com.recipemanager.api.ws;

import com.recipemanager.api.config.WsTokenService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

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

    // TODO: Validate the ws_token before allowing the handshake to proceed.
    //
    // 1. The job_id arrives as the last path segment — WebSocketConfig registers this
    //    handler at the wildcard pattern "/ws/jobs/*", not a URI template like
    //    "/ws/jobs/{jobId}", so there's no @PathVariable-style binding here. Pull it from
    //    the raw request path instead:
    //
    //      String path = request.getURI().getPath();
    //      UUID jobId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    //
    // 2. The token arrives as a query parameter, not a header — browsers' native
    //    WebSocket API cannot set custom headers on the handshake request, which is
    //    exactly why ws_token exists as a separate mechanism from the JWT Bearer token
    //    every other endpoint uses. UriComponentsBuilder can parse it out of the query
    //    string on request.getURI():
    //
    //      String token = UriComponentsBuilder.fromUri(request.getURI())
    //              .build().getQueryParams().getFirst("token");
    //
    // 3. Call wsTokenService.isValid(jobId, token). If invalid (or the path/token is
    //    malformed), set response.setStatusCode(HttpStatus.UNAUTHORIZED) and return false
    //    — returning false here stops the handshake before the socket ever upgrades.
    //
    // 4. If valid, stash jobId in `attributes` (the Map parameter) under a key such as
    //    "jobId". This is how data survives from the interceptor into the handler's
    //    session — JobStatusWebSocketHandler reads it back via session.getAttributes().
    //    Return true to let the handshake proceed.
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No-op — nothing to clean up after a completed handshake.
    }
}
