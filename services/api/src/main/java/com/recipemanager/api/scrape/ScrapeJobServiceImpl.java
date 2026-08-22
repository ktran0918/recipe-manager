package com.recipemanager.api.scrape;

import com.recipemanager.api.config.RabbitMqConfig;
import com.recipemanager.api.domain.ScrapeJob;
import com.recipemanager.api.exception.ApiException;
import com.recipemanager.api.repository.ScrapeJobRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class ScrapeJobServiceImpl implements ScrapeJobService {

    private final ScrapeJobRepository scrapeJobRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    public ScrapeJobServiceImpl(ScrapeJobRepository scrapeJobRepository,
                                StringRedisTemplate stringRedisTemplate,
                                RabbitTemplate rabbitTemplate) {
        this.scrapeJobRepository = scrapeJobRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public ParseResponse submitParse(UUID householdId, UUID userId, String url) {
        try {
            new URL(url).toURI();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid URL: " + url);
        }

        String dedupeKey = "recipe:scraped:" + sha256Hex(url);

        // Check Redis for a dedup match. If the key exists, return the existing job
        // without creating a new one or publishing to the queue.
        String existingJobId = stringRedisTemplate.opsForValue().get(dedupeKey);
        if (existingJobId != null) {
            Optional<ScrapeJob> existing = scrapeJobRepository.findById(UUID.fromString(existingJobId));
            if (existing.isPresent()) {
                ScrapeJob j = existing.get();
                return new ParseResponse(j.getId(), j.getStatus(), null);
            }
            // stale key — row was deleted after the dedup key was set; fall through and create a new job
        }

        ScrapeJob job = new ScrapeJob();
        job.setHouseholdId(householdId);
        job.setCreatedBy(userId);
        job.setSourceUrl(url);
        job.setStatus("pending");
        ScrapeJob saved = scrapeJobRepository.save(job);

        // Publish the message to RabbitMQ and set the dedup key in Redis.
        //
        // 1. Publish to the scrape.jobs queue.
        //    RabbitTemplate.convertAndSend(exchange, routingKey, message) serializes the
        //    message to JSON (once RabbitMqConfig wires up Jackson2JsonMessageConverter).
        //    Empty exchange ("") = default exchange, which routes by queue name as routing key.
        rabbitTemplate.convertAndSend("", RabbitMqConfig.QUEUE_NAME,
                new ScrapeJobMessage(saved.getId(), url, householdId));

        // 2. Write the dedup key so subsequent submissions of the same URL hit the cache.
        stringRedisTemplate.opsForValue().set(
                dedupeKey, saved.getId().toString(), 7, TimeUnit.DAYS);

        return new ParseResponse(saved.getId(), saved.getStatus(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(UUID jobId, UUID householdId) {
        ScrapeJob job = scrapeJobRepository.findByIdAndHouseholdId(jobId, householdId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found"));
        return new JobStatusResponse(job.getId(), job.getStatus(), job.getRecipeId(), job.getError());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}