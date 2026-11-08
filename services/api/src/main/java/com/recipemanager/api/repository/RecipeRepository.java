package com.recipemanager.api.repository;

import com.recipemanager.api.domain.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    List<Recipe> findByHouseholdId(UUID householdId);

    // Spring Data derives a paginated query from the method name — no @Query needed.
    // Pageable carries page number, page size, and optional sort. Page<T> wraps the results
    // with metadata (totalElements, totalPages, etc.).
    // C# equivalent: .Where(r => r.HouseholdId == id).Skip(...).Take(...).ToList()
    Page<Recipe> findByHouseholdId(UUID householdId, Pageable pageable);

    Optional<Recipe> findByIdAndHouseholdId(UUID id, UUID householdId);
}