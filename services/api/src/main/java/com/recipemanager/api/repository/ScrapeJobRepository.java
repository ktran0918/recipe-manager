package com.recipemanager.api.repository;

import com.recipemanager.api.domain.ScrapeJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID> {

    Optional<ScrapeJob> findByIdAndHouseholdId(UUID id, UUID householdId);
}