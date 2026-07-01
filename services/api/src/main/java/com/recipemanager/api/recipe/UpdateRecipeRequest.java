package com.recipemanager.api.recipe;

import java.math.BigDecimal;

// All fields nullable — PATCH semantics: only non-null fields are applied.
public record UpdateRecipeRequest(
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
        String[] dietTags
) {}
