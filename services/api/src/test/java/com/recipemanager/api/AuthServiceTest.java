package com.recipemanager.api;

import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.domain.User;
import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.dto.AuthResponse;
import com.recipemanager.api.dto.GoogleTokenResponse;
import com.recipemanager.api.dto.UserProfileResponse;
import com.recipemanager.api.exception.ApiException;
import com.recipemanager.api.repository.HouseholdMemberRepository;
import com.recipemanager.api.repository.UserRepository;
import com.recipemanager.api.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Pure unit test — no Spring context. AuthServiceImpl is instantiated directly with mocked
// collaborators. @InjectMocks creates the instance and wires @Mock fields via constructor.
// C# equivalent: new AuthService(mockUserRepo, mockJwtService, ...) in an xUnit test.
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private HouseholdMemberRepository memberRepository;
    @Mock private JwtService jwtService;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private AuthServiceImpl authService;

    private final UUID userId      = UUID.randomUUID();
    private final UUID householdId = UUID.randomUUID();

    // @Value fields are not injected by Mockito — use ReflectionTestUtils to set them.
    // C# equivalent: using reflection or a public setter in a test-only subclass.
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "googleClientId",     "test-client-id");
        ReflectionTestUtils.setField(authService, "googleClientSecret", "test-client-secret");
        ReflectionTestUtils.setField(authService, "googleRedirectUri",  "http://localhost/auth/google/callback");
    }

    @Test
    void buildGoogleAuthUrl_containsRequiredParams() {
        String url = authService.buildGoogleAuthUrl();

        assertThat(url).contains("accounts.google.com");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("test-client-id");
        assertThat(url).contains("scope");
    }

    // Verifies that handleGoogleCallback Step 1 POSTs the right form fields to Google's
    // token endpoint. Steps 2–4 are not yet implemented, so the method NPEs after the call —
    // that's expected. ArgumentCaptor captures the HttpEntity before the NPE so we can
    // inspect the form body.
    //
    // C# equivalent: mockHttpClient.Verify(c => c.PostAsync(..., It.Is<FormUrlEncodedContent>(
    //   f => f.ReadAsStringAsync().Result.Contains("code=my-code")), ...))
    @Test
    void handleGoogleCallback_step1_postsAuthCodeToGoogleTokenEndpoint() {
        // ArgumentCaptor intercepts the argument passed to the mock at call time.
        // Generic capture requires an unchecked cast; @SuppressWarnings keeps the compiler quiet.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(
                eq("https://oauth2.googleapis.com/token"),
                captor.capture(),
                eq(GoogleTokenResponse.class)
        )).thenReturn(ResponseEntity.ok(
                new GoogleTokenResponse("fake-access-token", "Bearer", 3600, null)));

        // Steps 2–4 are TODO — the method NPEs on user.getId() after step 1.
        // assertThatThrownBy lets us confirm the NPE without failing the test.
        assertThatThrownBy(() -> authService.handleGoogleCallback("my-auth-code"))
                .isInstanceOf(NullPointerException.class);

        // The captor now holds the HttpEntity that was sent to Google.
        MultiValueMap<String, String> formBody = captor.getValue().getBody();
        assertThat(formBody.getFirst("code")).isEqualTo("my-auth-code");
        assertThat(formBody.getFirst("grant_type")).isEqualTo("authorization_code");
        assertThat(formBody.getFirst("client_id")).isEqualTo("test-client-id");
        assertThat(formBody.getFirst("client_secret")).isEqualTo("test-client-secret");
        assertThat(formBody.getFirst("redirect_uri")).isEqualTo("http://localhost/auth/google/callback");
    }

    @Test
    void refreshToken_validToken_returnsNewTokenPair() {
        String incomingRefresh = UUID.randomUUID().toString();

        // Stub Redis: reverse lookup finds userId; session lookup confirms the token matches.
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:" + incomingRefresh)).thenReturn(userId.toString());
        when(valueOps.get("session:" + userId)).thenReturn(incomingRefresh);

        when(memberRepository.findById_UserId(userId)).thenReturn(List.of());
        when(jwtService.generateToken(eq(userId), isNull(), isNull())).thenReturn("new-access-token");

        AuthResponse result = authService.refreshToken(incomingRefresh);
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(incomingRefresh); // token was rotated

        verify(redis).delete("session:" + userId);
        verify(redis).delete("refresh:" + incomingRefresh);
        verify(valueOps, times(2)).set(anyString(), anyString(), any()); // two new Redis keys written
    }

    @Test
    void refreshToken_unknownToken_throws401() {
        String badToken = UUID.randomUUID().toString();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:" + badToken)).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshToken(badToken))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refreshToken_reusedToken_throws401AndInvalidatesSession() {
        String reusedToken   = UUID.randomUUID().toString();
        String currentToken  = UUID.randomUUID().toString(); // different — already rotated

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:" + reusedToken)).thenReturn(userId.toString());
        when(valueOps.get("session:" + userId)).thenReturn(currentToken); // mismatch

        assertThatThrownBy(() -> authService.refreshToken(reusedToken))
                .isInstanceOf(ApiException.class);
        verify(redis).delete("session:" + userId); // session invalidated on mismatch
    }

    @Test
    void logout_validToken_deletesRedisKeys() {
        String refreshToken = UUID.randomUUID().toString();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:" + refreshToken)).thenReturn(userId.toString());

        authService.logout(refreshToken);
        verify(redis).delete("session:" + userId);
        verify(redis).delete("refresh:" + refreshToken);
    }

    @Test
    void logout_unknownToken_isNoOp() {
        String unknownToken = UUID.randomUUID().toString();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:" + unknownToken)).thenReturn(null);

        authService.logout(unknownToken); // must not throw
        verify(redis, never()).delete(anyString());
    }

    @Test
    void getProfile_returnsUserData() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserPrincipal principal = new UserPrincipal(userId, householdId, "member");

        UserProfileResponse profile = authService.getProfile(principal);
        assertThat(profile.email()).isEqualTo("test@example.com");
        assertThat(profile.displayName()).isEqualTo("Test User");
        assertThat(profile.householdId()).isEqualTo(householdId);
        assertThat(profile.role()).isEqualTo("member");
    }
}