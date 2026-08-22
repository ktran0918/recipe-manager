package com.recipemanager.api.scrape;

import com.recipemanager.api.domain.ScrapeJob;
import com.recipemanager.api.repository.ScrapeJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapeJobServiceTest {

    @Mock private ScrapeJobRepository scrapeJobRepository;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private RabbitTemplate rabbitTemplate;

    private ScrapeJobServiceImpl service;

    private static final UUID HOUSEHOLD_ID  = UUID.randomUUID();
    private static final UUID USER_ID       = UUID.randomUUID();
    private static final UUID EXISTING_JOB  = UUID.randomUUID();
    private static final String RECIPE_URL  = "https://example.com/garlic-pasta";

    @BeforeEach
    void setUp() {
        service = new ScrapeJobServiceImpl(scrapeJobRepository, stringRedisTemplate, rabbitTemplate);
    }

    @Test
    void submitParse_duplicateUrl_returnsExistingJobWithoutCreatingNew() {
        // Implement after completing the Redis dedup TODO in ScrapeJobServiceImpl.submitParse().
        //
        // Arrange: prime the Redis mock so the dedup key is "already set" with EXISTING_JOB's ID.
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(EXISTING_JOB.toString());

        // ScrapeJob.id is @GeneratedValue with no public setter — ReflectionTestUtils
        // bypasses that so the mock entity can carry a known id for assertions.
        ScrapeJob existing = new ScrapeJob();
        ReflectionTestUtils.setField(existing, "id", EXISTING_JOB);
        existing.setStatus("complete");
        when(scrapeJobRepository.findById(EXISTING_JOB)).thenReturn(Optional.of(existing));

        // Act:
        ParseResponse response = service.submitParse(HOUSEHOLD_ID, USER_ID, RECIPE_URL);

        // Assert:
        assertThat(response.jobId()).isEqualTo(EXISTING_JOB);
        assertThat(response.status()).isEqualTo("complete");
        verify(scrapeJobRepository, never()).save(any());    // no new job created
        verify(rabbitTemplate, never())
                .convertAndSend(anyString(), anyString(), any(ScrapeJobMessage.class)); // not re-published
    }
}