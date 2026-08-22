package com.recipemanager.api.scrape;

import java.util.UUID;

public record ParseResponse(UUID jobId, String status, String wsToken) {}
