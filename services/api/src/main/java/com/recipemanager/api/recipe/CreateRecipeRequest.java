package com.recipemanager.api.recipe;

import java.math.BigDecimal;
import java.util.List;

public record CreateRecipeRequest(
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
        List<IngredientLineRequest> ingredients,
        List<StepRequest> steps
) {}
