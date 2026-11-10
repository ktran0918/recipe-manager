package com.recipemanager.api.ws;

import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.domain.Household;
import com.recipemanager.api.domain.User;
import com.recipemanager.api.repository.HouseholdRepository;
import com.recipemanager.api.repository.UserRepository;
import com.recipemanager.api.scrape.ParseRequest;
import com.recipemanager.api.scrape.ParseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Full-stack integration test: a real client opens /ws/jobs/{jobId}, a message is published
// to Redis exactly the way the scraper publishes it, and the test asserts the client receives
// it and the server closes the connection afterward.
//
// C# equivalent: TestServer + a raw ClientWebSocket connecting to a SignalR-free WebSocket
// endpoint, then publishing through StackExchange.Redis and asserting the client's receive
// loop got the message.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class JobStatusWebSocketIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("recipe_manager_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static ElasticsearchContainer elasticsearchContainer =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.0")
                    .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",             () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",     () -> "validate");
        registry.add("spring.data.redis.url",
                () -> "redis://localhost:" + redisContainer.getMappedPort(6379));
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearchContainer.getHttpHostAddress());
        registry.add("spring.security.oauth2.client.registration.google.client-id",     () -> "test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0yNTYtYml0cy1sb25nLXBhZGRlZA==");
    }

    @LocalServerPort int port;

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired HouseholdRepository householdRepository;
    @Autowired StringRedisTemplate stringRedisTemplate;

    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("ws-test@example.com");
        user.setDisplayName("WS Tester");
        user.setOauthProvider("google");
        user.setOauthId("google-id-ws-test");
        user = userRepository.save(user);

        Household household = new Household();
        household.setName("WS Test Household");
        household.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        household = householdRepository.save(household);

        accessToken = jwtService.generateToken(user.getId(), household.getId(), "member");
    }

    @Test
    void jobStatusUpdate_isForwardedToClient_andConnectionCloses() throws Exception {
        // Go through the real POST /recipes/parse flow so jobId and wsToken are the genuine
        // values the running server actually issued and stored — not fixtures the test invents.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<ParseRequest> request = new HttpEntity<>(
                new ParseRequest("https://example.com/ws-test-recipe"), headers);
        ResponseEntity<ParseResponse> parseResponse =
                testRestTemplate.postForEntity("/recipes/parse", request, ParseResponse.class);
        assertThat(parseResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ParseResponse body = parseResponse.getBody();
        assertThat(body).isNotNull();
        UUID jobId = body.jobId();
        String wsToken = body.wsToken();

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.offer(message.getPayload());
            }
        };

        URI uri = URI.create("ws://localhost:" + port + "/api/v1/ws/jobs/" + jobId + "?token=" + wsToken);
        WebSocketSession session = new StandardWebSocketClient()
                .execute(clientHandler, uri.toString())
                .get(5, TimeUnit.SECONDS);

        try {
            // Simulate exactly what the scraper publishes on completion (see
            // scraper/notifications.py publish_job_complete) — same channel, same JSON shape.
            String payload = "{\"status\":\"complete\",\"recipe_id\":\"" + UUID.randomUUID() + "\"}";

            // RedisMessageListenerContainer.addMessageListener() schedules the actual Redis
            // SUBSCRIBE on a background task and returns before it necessarily completes — a
            // real race only visible in a test that publishes immediately after connecting.
            // In production this never matters: the scraper always publishes well after a
            // client connects, since it has to fetch/parse/embed first. Retrying the publish
            // a few times bridges that subscription delay without a blind, flaky sleep.
            String message = null;
            for (int attempt = 0; attempt < 5 && message == null; attempt++) {
                stringRedisTemplate.convertAndSend("job.status." + jobId, payload);
                message = received.poll(1, TimeUnit.SECONDS);
            }
            assertThat(message).isEqualTo(payload);

            // The server closes the connection itself once it forwards a terminal status —
            // give it a moment, then confirm the session is no longer open.
            Thread.sleep(500);
            assertThat(session.isOpen()).isFalse();
        } finally {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
    }
}
