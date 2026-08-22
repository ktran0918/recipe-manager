package com.recipemanager.api.scrape;

import java.util.UUID;

public interface ScrapeJobService {

    ParseResponse submitParse(UUID householdId, UUID userId, String url);

    JobStatusResponse getJobStatus(UUID jobId, UUID householdId);
}