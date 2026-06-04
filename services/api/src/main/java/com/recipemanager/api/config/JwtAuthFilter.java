package com.recipemanager.api.config;

import com.recipemanager.api.domain.UserPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.List;

// Intercepts every request, extracts the Bearer token, and populates the SecurityContext.
// Extends OncePerRequestFilter — guaranteed to run exactly once per request even in
// async dispatch chains. C# equivalent: a custom middleware added to app.Use(...).
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // Intercepts every request, extracts the Bearer token, and populates the SecurityContext.
    // C# equivalent: the block inside a custom middleware's InvokeAsync that validates a JWT
    // and calls httpContext.User = new ClaimsPrincipal(...) before await next(context).
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1. Read the Authorization header. If it's missing or doesn't start with "Bearer ",
        //    call filterChain.doFilter(request, response) and return — let the SecurityFilterChain
        //    decide whether the route requires auth (public routes pass through; protected routes
        //    will be rejected by the authorizeHttpRequests config in SecurityConfig).
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract the token string: authHeader.substring(7)
        String token = authHeader.substring(7);
        // 3. Extract principal from token and catch JwtException.
        //    Token is validated inside jwtService.extractPrincipal()
        //    On JwtException: write a 401 JSON response and return without calling doFilter.
        UserPrincipal principal;
        try {
            principal = jwtService.extractPrincipal(token);
        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + e.getMessage() + "\",\"status\":401}");
            return;
        }
        // 4. Build a UsernamePasswordAuthenticationToken (Spring's generic Authentication impl):
        //      new UsernamePasswordAuthenticationToken(principal, null, authorities)
        //    The authorities list should contain one SimpleGrantedAuthority.
        //    Role is stored as-is (e.g. "owner") — Spring expects "ROLE_OWNER" for hasRole() checks,
        //    but we check role manually in service code, so a plain authority string is fine.
        List<SimpleGrantedAuthority> authorities = principal.role() != null
                ? List.of(new SimpleGrantedAuthority(principal.role()))
                : List.of();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null,
                authorities);
        // 5. Attach request details (IP, session) — boilerplate Spring Security convention:
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        // 6. Set the authentication on the SecurityContext:
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 7. Continue to the SecurityFilterChain
        filterChain.doFilter(request, response);
    }
}
