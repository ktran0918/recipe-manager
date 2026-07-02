package com.recipemanager.api.repository;

import com.recipemanager.api.domain.Recipe;
import com.recipemanager.api.domain.RecipeNutrition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecipeNutritionRepository extends JpaRepository<RecipeNutrition, UUID> {

    Optional<RecipeNutrition> findByRecipe(Recipe recipe);
}