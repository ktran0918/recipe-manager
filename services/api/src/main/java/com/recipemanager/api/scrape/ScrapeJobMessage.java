package com.recipemanager.api.scrape;

import java.util.UUID;

// Payload published to the scrape.jobs RabbitMQ queue.
// The Python scraper consumer deserialises this from JSON.
public record ScrapeJobMessage(UUID jobId, String url, UUID householdId) {}