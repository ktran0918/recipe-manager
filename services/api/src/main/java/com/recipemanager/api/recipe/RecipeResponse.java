package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.Recipe;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecipeResponse(
        UUID id,
        UUID householdId,
        UUID createdBy,
        String title,
        String description,
        String sourceUrl,
        String imageUrl,
        BigDecimal servings,
        Integer cookTimeMinutes,
        Integer prepTimeMinutes,
        String complexity,
        String[] occasions,
        String cuisine,
        String[] dietTags,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<IngredientLineResponse> ingredients,
        List<StepResponse> steps
) {
    // Standard factory — maps entity to response with original quantities.
    public static RecipeResponse from(Recipe recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getHouseholdId(),
                recipe.getCreatedBy(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getSourceUrl(),
                recipe.getImageUrl(),
                recipe.getServings(),
                recipe.getCookTimeMinutes(),
                recipe.getPrepTimeMinutes(),
                recipe.getComplexity(),
                recipe.getOccasions(),
                recipe.getCuisine(),
                recipe.getDietTags(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt(),
                recipe.getIngredients().stream().map(IngredientLineResponse::from).toList(),
                recipe.getSteps().stream().map(StepResponse::from).toList()
        );
    }

    // Scaling factory — accepts a pre-scaled ingredient list built by RecipeServiceImpl.
    // Quantities and steps come from separate lists because only quantities are scaled.
    public static RecipeResponse from(Recipe recipe, List<IngredientLineResponse> scaledIngredients) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getHouseholdId(),
                recipe.getCreatedBy(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getSourceUrl(),
                recipe.getImageUrl(),
                recipe.getServings(),
                recipe.getCookTimeMinutes(),
                recipe.getPrepTimeMinutes(),
                recipe.getComplexity(),
                recipe.getOccasions(),
                recipe.getCuisine(),
                recipe.getDietTags(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt(),
                scaledIngredients,
                recipe.getSteps().stream().map(StepResponse::from).toList()
        );
    }
}