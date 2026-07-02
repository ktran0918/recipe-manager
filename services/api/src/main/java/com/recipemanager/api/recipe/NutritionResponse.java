package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.RecipeNutrition;
import java.math.BigDecimal;

public record NutritionResponse(
        BigDecimal calories,
        BigDecimal proteinG,
        BigDecimal carbsG,
        BigDecimal fatG,
        BigDecimal fiberG,
        BigDecimal sodiumMg,
        String servingSizeLabel
) {
    public static NutritionResponse from(RecipeNutrition n) {
        return new NutritionResponse(
                n.getCalories(),
                n.getProteinG(),
                n.getCarbsG(),
                n.getFatG(),
                n.getFiberG(),
                n.getSodiumMg(),
                n.getServingSizeLabel());
    }
}