package com.recipemanager.api;

import com.recipemanager.api.config.JwtAuthFilter;
import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.domain.UserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// Pure unit test — no Spring context loaded. The filter is instantiated directly with
// a mocked JwtService. MockHttpServletRequest/Response stand in for the real HTTP objects.
// C# equivalent: testing middleware with a mock RequestDelegate and DefaultHttpContext.
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private FilterChain filterChain;

    // @InjectMocks creates the filter and injects the @Mock fields via constructor.
    @InjectMocks private JwtAuthFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private final UUID userId      = UUID.randomUUID();
    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    // Clear the SecurityContext after each test so state doesn't leak between tests.
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_populatesPrincipal() throws Exception {
        // Arrange: stub a valid token and the Claims it produces.
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.extractPrincipal("valid-token"))
                .thenReturn(new UserPrincipal(userId, householdId, "member"));


        filter.doFilter(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

        // Assert that the principal is a UserPrincipal with the correct userId, householdId, and role.
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.householdId()).isEqualTo(householdId);
        assertThat(principal.role()).isEqualTo("member");

        // Assert that filterChain.doFilter() was called
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void expiredToken_returns401() throws Exception {
        // Arrange: stub extractPrincipal to throw ExpiredJwtException.
        request.addHeader("Authorization", "Bearer expired-token");
        when(jwtService.extractPrincipal("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "Token expired"));


        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("error");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void tamperedToken_returns401() throws Exception {
        // Arrange: stub extractPrincipal to throw a generic JwtException (bad signature).
        request.addHeader("Authorization", "Bearer tampered-token");
        when(jwtService.extractPrincipal("tampered-token"))
                .thenThrow(new JwtException("Invalid signature"));


        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void missingAuthHeader_continuesChainUnauthenticated() throws Exception {
        // No Authorization header set on request.

        filter.doFilter(request, response, filterChain);
        // Assert that filterChain.doFilter WAS called (filter must not block the request —
        // SecurityConfig's authorizeHttpRequests decides whether the route needs auth).
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // Assert that jwtService was never called.
        verifyNoInteractions(jwtService);

    }
}