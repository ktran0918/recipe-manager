package com.recipemanager.api.scrape;

import java.util.UUID;

public record JobStatusResponse(UUID jobId, String status, UUID recipeId, String error) {}