package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.Recipe;
import com.recipemanager.api.domain.RecipeStep;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record CookModeResponse(
        UUID recipeId,
        String title,
        Integer prepTimeMinutes,
        Integer cookTimeMinutes,
        BigDecimal servings,
        List<CookModeIngredientResponse> ingredients,
        List<String> directions,
        NutritionResponse nutrition,
        String sourceUrl
) {
    public static CookModeResponse from(
            Recipe recipe,
            BigDecimal servings,
            List<CookModeIngredientResponse> ingredients,
            NutritionResponse nutrition) {
        // Comparator.comparingInt extracts an int key from each element for ordering.
        // C# equivalent: .OrderBy(s => s.StepNumber).Select(s => s.Instruction).ToList()
        List<String> directions = recipe.getSteps().stream()
                .sorted(Comparator.comparingInt(RecipeStep::getStepNumber))
                .map(RecipeStep::getInstruction)
                .toList();
        return new CookModeResponse(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getPrepTimeMinutes(),
                recipe.getCookTimeMinutes(),
                servings,
                ingredients,
                directions,
                nutrition,
                recipe.getSourceUrl());
    }
}