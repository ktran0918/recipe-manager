package com.recipemanager.api.service.impl;

import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.domain.HouseholdMember;
import com.recipemanager.api.domain.User;
import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.dto.AuthResponse;
import com.recipemanager.api.dto.GoogleTokenResponse;
import com.recipemanager.api.dto.GoogleUserInfo;
import com.recipemanager.api.dto.UserProfileResponse;
import com.recipemanager.api.exception.ApiException;
import com.recipemanager.api.repository.HouseholdMemberRepository;
import com.recipemanager.api.repository.UserRepository;
import com.recipemanager.api.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.UUID;

// Orchestrates the Google OAuth2 flow, JWT issuance, and refresh token lifecycle.
// C# equivalent: a scoped service class registered via services.AddScoped<IAuthService, AuthServiceImpl>().
@Service
public class AuthServiceImpl implements AuthService {

    private static final String GOOGLE_AUTH_URL  = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_INFO_URL  = "https://www.googleapis.com/oauth2/v3/userinfo";

    // Redis key prefixes — "session:{userId}" holds the current refresh UUID for that user;
    // "refresh:{uuid}" is the reverse lookup so we can find the user from an incoming token.
    private static final String SESSION_PREFIX = "session:";
    private static final String REFRESH_PREFIX = "refresh:";
    private static final Duration REFRESH_TTL  = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final HouseholdMemberRepository memberRepository;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final RestTemplate restTemplate;

    // @Value injects from application.yml / environment variables.
    // C# equivalent: IOptions<GoogleOAuthSettings> injected via the options pattern.
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    public AuthServiceImpl(UserRepository userRepository,
                           HouseholdMemberRepository memberRepository,
                           JwtService jwtService,
                           StringRedisTemplate redis,
                           RestTemplate restTemplate) {

        this.userRepository  = userRepository;
        this.memberRepository = memberRepository;
        this.jwtService      = jwtService;
        this.redis           = redis;
        this.restTemplate    = restTemplate;
    }

    // Build the Google authorization URL that the browser is redirected to.
    // Google redirects back to our redirect_uri with ?code=... after the user consents.
    @Override
    public String buildGoogleAuthUrl() {
        // UriComponentsBuilder URL-encodes query values automatically.
        // C# equivalent: new UriBuilder("https://.
        // ..") { Query = "response_type=code&..." }.ToString()
        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id",     googleClientId)
                .queryParam("redirect_uri",  googleRedirectUri)
                .queryParam("scope",         "openid email profile")
                .queryParam("access_type",   "offline")
                .build().toUriString();
    }

    // Full callback flow: code → Google tokens → Google profile → upsert user → issue JWT pair.
    // @Transactional wraps the DB writes (user upsert) in a transaction so a failure
    // mid-method doesn't leave a partial user row.
    // C# equivalent: [Transaction] or a using block around a DbContext.SaveChanges() call.
    @Override
    @Transactional
    public AuthResponse handleGoogleCallback(String code) {

        // Step 1 — Exchange the authorization code for a Google access token.
        // POST to GOOGLE_TOKEN_URL with application/x-www-form-urlencoded body.
        // RestTemplate sends form data via HttpEntity<MultiValueMap<String, String>>.
        //
        // C# equivalent:
        //   var content = new FormUrlEncodedContent(new[] { new KeyValuePair<string,string>("code", code), ... });
        //   var response = await httpClient.PostAsync(GOOGLE_TOKEN_URL, content);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code",          code);
        body.add("client_id",     googleClientId);
        body.add("client_secret", googleClientSecret);
        body.add("redirect_uri",  googleRedirectUri);
        body.add("grant_type",    "authorization_code");

        GoogleTokenResponse tokenResponse;
        try {
            ResponseEntity<GoogleTokenResponse> tokenResp =
                restTemplate.postForEntity(GOOGLE_TOKEN_URL,
                    new HttpEntity<>(body, headers), GoogleTokenResponse.class);
            tokenResponse = tokenResp.getBody();
        } catch (HttpClientErrorException e) {
            // Google returns 400 for an invalid/expired/already-used code or a
            // redirect_uri mismatch — surface as 401 instead of an opaque 500.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google rejected the authorization code");
        }

        // Step 2 — Fetch the authenticated user's profile from Google.
        // GET GOOGLE_INFO_URL with Authorization: Bearer <accessToken>.
        GoogleUserInfo userInfo;
        try {
            HttpHeaders infoHeaders = new HttpHeaders();
            infoHeaders.setBearerAuth(tokenResponse.accessToken());
            ResponseEntity<GoogleUserInfo> infoResp =
                restTemplate.exchange(GOOGLE_INFO_URL, HttpMethod.GET,
                    new HttpEntity<>(infoHeaders), GoogleUserInfo.class);
            userInfo = infoResp.getBody();
        } catch (HttpClientErrorException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Failed to fetch Google user profile");
        }

        // Step 3 — Upsert the user in the database.
        // "sub" is the stable, immutable Google user ID — the right key for dedup.
        // Updating email/name/avatar on every login keeps stale data from persisting.
           User user = userRepository
               .findByOauthProviderAndOauthId("google", userInfo.sub())
               .map(existing -> {
                   existing.setEmail(userInfo.email());
                   existing.setDisplayName(userInfo.name());
                   existing.setAvatarUrl(userInfo.picture());
                   return existing;
               })
               .orElseGet(() -> {
                   User newUser = new User();
                   newUser.setOauthProvider("google");
                   newUser.setOauthId(userInfo.sub());
                   newUser.setEmail(userInfo.email());
                   newUser.setDisplayName(userInfo.name());
                   newUser.setAvatarUrl(userInfo.picture());
                   return newUser;
               });
           user = userRepository.save(user);

        // Step 4 — Look up this user's household membership.
        // A newly registered user has no household yet — householdId and role will be null,
        // and the JWT will reflect that. The client must call POST /households to join one.
        //
           var membership = memberRepository.findById_UserId(user.getId())
               .stream().findFirst();
           UUID householdId = membership.map(m -> m.getHousehold().getId()).orElse(null);
           String role      = membership.map(m -> m.getRole()).orElse(null);

        return issueTokens(user.getId(), householdId, role);
    }

    // Validate a refresh token and rotate it — the old token is deleted and a new pair issued.
    // Token rotation means a stolen refresh token can only be used once before it's invalidated.
    @Override
    public AuthResponse refreshToken(String refreshToken) {

        // Step 1 — Reverse-lookup: find the userId stored under "refresh:{token}".

           String userIdStr = redis.opsForValue().get(REFRESH_PREFIX + refreshToken);
           if (userIdStr == null) {
               throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
           }

        // Step 2 — Verify ownership: the "session:{userId}" key must hold this exact token.
        // If it doesn't match, another device has already rotated this token — reject it.

           String storedToken = redis.opsForValue().get(SESSION_PREFIX + userIdStr);
           if (!refreshToken.equals(storedToken)) {
               // Possible replay attack — delete the session to force re-login.
               redis.delete(SESSION_PREFIX + userIdStr);
               throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token already used");
           }

        // Step 3 — Delete the old keys and issue a fresh pair.
           redis.delete(SESSION_PREFIX + userIdStr);
           redis.delete(REFRESH_PREFIX + refreshToken);

           UUID userId = UUID.fromString(userIdStr);
           var membership = memberRepository.findById_UserId(userId).stream().findFirst();
           UUID householdId = membership.map(m -> m.getHousehold().getId()).orElse(null);
           String role      = membership.map(HouseholdMember::getRole).orElse(null);
          return issueTokens(userId, householdId, role);
    }

    // Remove the refresh token from Redis. No-op if the token is already gone.
    @Override
    public void logout(String refreshToken) {

           String userIdStr = redis.opsForValue().get(REFRESH_PREFIX + refreshToken);
           if (userIdStr != null) {
               redis.delete(SESSION_PREFIX + userIdStr);
               redis.delete(REFRESH_PREFIX + refreshToken);
           }
    }

    // Fetch the current user's profile. householdId and role come from the JWT principal —
    // no extra DB query needed for those fields.
    @Override
    public UserProfileResponse getProfile(UserPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                principal.householdId(),
                principal.role()
        );
    }

    // Generate a JWT access token + refresh UUID, store both Redis keys, and return them.
    // Called by handleGoogleCallback and refreshToken.
    private AuthResponse issueTokens(UUID userId, UUID householdId, String role) {
        String accessToken  = jwtService.generateToken(userId, householdId, role);
        String refreshToken = UUID.randomUUID().toString();

        // Bidirectional mapping so we can:
        //   - Validate ownership:   GET session:{userId}   → must equal the incoming refreshToken
        //   - Find user from token: GET refresh:{token}    → userId (for the refresh endpoint)
        redis.opsForValue().set(SESSION_PREFIX + userId,       refreshToken, REFRESH_TTL);
        redis.opsForValue().set(REFRESH_PREFIX + refreshToken, userId.toString(), REFRESH_TTL);

        return new AuthResponse(accessToken, refreshToken);
    }
}