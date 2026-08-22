package com.recipemanager.api.scrape;

import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/recipes")
public class ScrapeJobController {

    private final ScrapeJobService scrapeJobService;

    public ScrapeJobController(ScrapeJobService scrapeJobService) {
        this.scrapeJobService = scrapeJobService;
    }

    // 202 Accepted — the work happens asynchronously; the caller polls /jobs/:id for progress.
    @PostMapping("/parse")
    public ResponseEntity<ParseResponse> submitParse(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ParseRequest request) {
        requireHousehold(principal);
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "url is required");
        }
        ParseResponse response = scrapeJobService.submitParse(
                principal.householdId(), principal.userId(), request.url());
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        requireHousehold(principal);
        return ResponseEntity.ok(scrapeJobService.getJobStatus(id, principal.householdId()));
    }

    private void requireHousehold(UserPrincipal principal) {
        if (principal.householdId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Must belong to a household");
        }
    }
}
