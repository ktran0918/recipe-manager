package com.recipemanager.api.recipe;

import java.math.BigDecimal;

public record IngredientLineRequest(
        String ingredientName,
        BigDecimal quantity,
        String unit,
        String preparation,
        boolean optional,
        String rawText
) {}
