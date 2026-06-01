package com.recipemanager.api.repository;

import com.recipemanager.api.domain.RecipeStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, UUID> {

    List<RecipeStep> findByRecipe_IdOrderByStepNumberAsc(UUID recipeId);
}
