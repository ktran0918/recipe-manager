package com.recipemanager.api;

import com.recipemanager.api.dto.AuthResponse;
import com.recipemanager.api.dto.GoogleTokenResponse;
import com.recipemanager.api.dto.GoogleUserInfo;
import com.recipemanager.api.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

// Full-stack integration test: real PostgreSQL + real Redis via Testcontainers.
// Google's HTTP API is replaced by a @MockBean so no real network calls are made.
//
// @SpringBootTest(webEnvironment = RANDOM_PORT) starts the full server.
// TestRestTemplate makes real HTTP calls to it — exercises the full request pipeline
// including JwtAuthFilter, SecurityConfig, controllers, and service layer.
//
// C# equivalent: WebApplicationFactory<Program> with a mocked IHttpClientFactory and
// a Testcontainers PostgreSQL fixture.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("recipe_manager_test")
            .withUsername("test")
            .withPassword("test");

    // GenericContainer is used for services without a dedicated Testcontainers module.
    // .withExposedPorts(6379) maps the container's port; getMappedPort() gives the host port.
    // C# equivalent: new TestcontainersBuilder<RedisTestcontainer>().WithImage("redis:7-alpine")...
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",             () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",     () -> "validate");
        registry.add("spring.data.redis.url",
                () -> "redis://localhost:" + redisContainer.getMappedPort(6379));
        // Provide non-placeholder OAuth2 values so Spring Boot's autoconfiguration
        // does not reject the client registration at startup.
        registry.add("spring.security.oauth2.client.registration.google.client-id",     () -> "test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
        // Provide a valid Base64-encoded JWT secret (>= 256 bits) for the test context.
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0yNTYtYml0cy1sb25nLXBhZGRlZA==");
    }

    // @MockBean replaces the RestTemplate bean in the Spring context with a Mockito mock.
    // All calls AuthServiceImpl makes to Google's API will be intercepted here.
    // C# equivalent: services.Replace(ServiceDescriptor.Singleton<HttpClient>(mockClient))
    //   inside WebApplicationFactory.ConfigureWebHost().
    @MockBean
    RestTemplate restTemplate;

    @Autowired
    TestRestTemplate testRestTemplate;

    @Test
    void googleCallback_createsUserAndReturnsJwt() {
        // Arrange: stub Google's token exchange endpoint.
        when(restTemplate.postForEntity(contains("oauth2.googleapis.com/token"),
                any(), eq(GoogleTokenResponse.class)))
            .thenReturn(ResponseEntity.ok(
                new GoogleTokenResponse("google-access-token", "Bearer", 3600, null)));

        // Arrange: stub Google's userinfo endpoint.
        when(restTemplate.exchange(contains("googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET), any(), eq(GoogleUserInfo.class)))
            .thenReturn(ResponseEntity.ok(
                new GoogleUserInfo("google-sub-123", "test@example.com", "Test User", null)));

        // Act: call the callback with a fake authorization code.
        ResponseEntity<AuthResponse> callbackResp =
            testRestTemplate.getForEntity("/auth/google/callback?code=fake-code", AuthResponse.class);
        assertThat(callbackResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(callbackResp.getBody().accessToken()).isNotBlank();
        assertThat(callbackResp.getBody().refreshToken()).isNotBlank();

        // Act: call /auth/me with the access JWT — verifies the full pipeline end-to-end.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callbackResp.getBody().accessToken());
        ResponseEntity<UserProfileResponse> meResp =
            testRestTemplate.exchange("/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), UserProfileResponse.class);
        assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResp.getBody().email()).isEqualTo("test@example.com");
        assertThat(meResp.getBody().displayName()).isEqualTo("Test User");
    }

    @Test
    void protectedRoute_withoutToken_returns401() {
        ResponseEntity<String> resp =
            testRestTemplate.getForEntity("/auth/me", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}