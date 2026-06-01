package com.recipemanager.api.repository;

import com.recipemanager.api.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    List<Recipe> findByHouseholdId(UUID householdId);

    Optional<Recipe> findByIdAndHouseholdId(UUID id, UUID householdId);
}
