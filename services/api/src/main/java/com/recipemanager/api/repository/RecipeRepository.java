package com.recipemanager.api.repository;

import com.recipemanager.api.domain.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // @Query accepts JPQL — entity class names and field names, not table/column names.
    //
    // JPQL has no ILIKE, so simulate it:
    //   LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
    // Named parameters (:keyword) require @Param("keyword") on the method argument.
    // The Pageable parameter is handled automatically — do not reference it in the JPQL.
    //
    // C# equivalent:
    //   .Where(r => r.HouseholdId == id &&
    //               (EF.Functions.ILike(r.Title, $"%{keyword}%") ||
    //                EF.Functions.ILike(r.Description, $"%{keyword}%")))
    @Query("SELECT recipe FROM Recipe recipe WHERE recipe.householdId = :householdId " +
            "AND (LOWER(recipe.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR LOWER(recipe.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Recipe> findByHouseholdIdAndKeyword(
            @Param("householdId") UUID householdId,
            @Param("keyword") String keyword,
            Pageable pageable);
}