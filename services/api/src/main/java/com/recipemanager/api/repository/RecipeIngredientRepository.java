package com.recipemanager.api.repository;

import com.recipemanager.api.domain.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {

    List<RecipeIngredient> findByRecipe_IdOrderBySortOrderAsc(UUID recipeId);
}
