package com.recipemanager.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// @Configuration tells Spring this class produces @Bean methods to register in the DI container.
// @EnableWebSecurity activates Spring Security's filter chain machinery.
// C# equivalent: calling builder.Services.AddAuthentication(...) + AddAuthorization(...) in Program.cs,
// except the setup lives in a class rather than inline builder calls.
//
// PLACEHOLDER — EP1-05 replaces this with the full JWT filter + route policy.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // SecurityFilterChain is a chain of servlet filters that runs on every HTTP request before
    // it reaches any controller — same concept as ASP.NET Core middleware. Spring Security
    // builds the chain from what you configure on the HttpSecurity builder here.
    // Without this bean, Spring Boot defaults to redirecting all traffic to OAuth2 login.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF attacks exploit session cookies. We use stateless JWTs, so there is no
            // server-side session for an attacker to hijack — disabling CSRF is correct here.
            .csrf(csrf -> csrf.disable())

            // Never create an HttpSession. Without this, Spring Security would hand out
            // JSESSIONID cookies even though we intend to be fully stateless.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Route access policy — C# equivalent of [Authorize] attributes, but defined
            // centrally rather than per-controller. Rules are evaluated top-to-bottom;
            // first match wins.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()  // public monitoring
                .requestMatchers("/auth/**").permitAll()                             // login flow — no token yet
                .anyRequest().authenticated()                                       // everything else needs a JWT
            )

            // Tells Spring where to send unauthenticated requests that hit a protected route.
            // Stub — EP1-05 and EP1-06 replace this with our own JWT issuance flow.
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/auth/google")
            );

        return http.build();
    }
}
