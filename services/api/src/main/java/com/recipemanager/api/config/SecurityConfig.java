package com.recipemanager.api.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Full SecurityFilterChain — replaces the EP1-02 placeholder.
// EP1-06 adds the OAuth2 login endpoints (/auth/google, /auth/google/callback) as
// regular @RestController methods; Spring Security does not manage the OAuth dance here.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            // Insert JwtAuthFilter before Spring's UsernamePasswordAuthenticationFilter.
            // This is the standard position for token filters — it runs after the request
            // is parsed but before Spring's own auth processing.
            // C# equivalent: app.UseAuthentication() placed before app.UseAuthorization().
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // authenticationEntryPoint handles requests that reach a protected route
            // without a valid authentication object in the SecurityContext.
            // JwtAuthFilter already wrote a 401 for bad tokens — this catches the case
            // where the header was missing entirely and the route is protected.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Authentication required\",\"status\":401}");
                })
            );

        return http.build();
    }
}