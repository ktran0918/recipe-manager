package com.recipemanager.api.scrape;

import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.config.RabbitMqConfig;
import com.recipemanager.api.domain.Household;
import com.recipemanager.api.domain.ScrapeJob;
import com.recipemanager.api.domain.User;
import com.recipemanager.api.repository.HouseholdRepository;
import com.recipemanager.api.repository.ScrapeJobRepository;
import com.recipemanager.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Full-stack integration test: real PostgreSQL + real Redis + real RabbitMQ via Testcontainers.
// Confirms the whole POST /recipes/parse pipeline — DB row persisted, HTTP response shaped
// correctly, and the message actually lands on the scrape.jobs queue for the Python scraper.
//
// C# equivalent: WebApplicationFactory<Program> with Testcontainers Postgres/Redis/RabbitMQ fixtures.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ScrapeJobIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("recipe_manager_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // RabbitMQContainer is Testcontainers' dedicated module for RabbitMQ — it exposes
    // getHost()/getAmqpPort() for wiring the broker connection. Image tag matches
    // docker-compose.yml so local and CI runs exercise the same broker version.
    // C# equivalent: Testcontainers.RabbitMq's RabbitMqBuilder in an xUnit fixture.
    @Container
    static RabbitMQContainer rabbitMq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",             () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",     () -> "validate");
        registry.add("spring.data.redis.url",
                () -> "redis://localhost:" + redisContainer.getMappedPort(6379));
        registry.add("spring.rabbitmq.host",     rabbitMq::getHost);
        registry.add("spring.rabbitmq.port",     rabbitMq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMq::getAdminPassword);
        // Provide non-placeholder OAuth2 values so Spring Boot's autoconfiguration
        // does not reject the client registration at startup.
        registry.add("spring.security.oauth2.client.registration.google.client-id",     () -> "test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0yNTYtYml0cy1sb25nLXBhZGRlZA==");
    }

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired HouseholdRepository householdRepository;
    @Autowired ScrapeJobRepository scrapeJobRepository;
    @Autowired RabbitTemplate rabbitTemplate;

    private String accessToken;
    private UUID householdId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("scraper-test@example.com");
        user.setDisplayName("Scraper Tester");
        user.setOauthProvider("google");
        user.setOauthId("google-id-scraper-test");
        user = userRepository.save(user);

        Household household = new Household();
        household.setName("Test Household");
        household.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        household = householdRepository.save(household);
        householdId = household.getId();

        // Mint the JWT directly rather than going through the OAuth callback —
        // this test is exercising the scrape pipeline, not the auth flow.
        accessToken = jwtService.generateToken(user.getId(), householdId, "member");
    }

    @Test
    void submitParse_createsJobRowAndPublishesToQueue() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<ParseRequest> request = new HttpEntity<>(
                new ParseRequest("https://example.com/garlic-pasta"), headers);

        ResponseEntity<ParseResponse> response =
                testRestTemplate.postForEntity("/recipes/parse", request, ParseResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        UUID jobId = response.getBody().jobId();

        // Job row exists in Postgres with status=pending.
        Optional<ScrapeJob> saved = scrapeJobRepository.findById(jobId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("pending");
        assertThat(saved.get().getHouseholdId()).isEqualTo(householdId);
        assertThat(saved.get().getSourceUrl()).isEqualTo("https://example.com/garlic-pasta");

        // Message actually landed on the scrape.jobs queue. receiveAndConvert blocks up to
        // the timeout — the publish is synchronous but the broker needs a moment to route it.
        Object payload = rabbitTemplate.receiveAndConvert(RabbitMqConfig.QUEUE_NAME, 5000L);
        assertThat(payload).isInstanceOf(ScrapeJobMessage.class);
        ScrapeJobMessage message = (ScrapeJobMessage) payload;
        assertThat(message.jobId()).isEqualTo(jobId);
        assertThat(message.url()).isEqualTo("https://example.com/garlic-pasta");
        assertThat(message.householdId()).isEqualTo(householdId);
    }
}