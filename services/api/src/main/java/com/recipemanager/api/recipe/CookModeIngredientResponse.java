package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.RecipeIngredient;
import java.math.BigDecimal;

public record CookModeIngredientResponse(
        String name,
        BigDecimal quantity,
        String unit,
        String preparation,
        boolean optional
) {
    public static CookModeIngredientResponse from(RecipeIngredient ri) {
        return new CookModeIngredientResponse(
                ri.getIngredient().getName(),
                ri.getQuantity(),
                ri.getUnit(),
                ri.getPreparation(),
                ri.isOptional());
    }

    // C# equivalent: this with { Quantity = scaledQuantity }
    public CookModeIngredientResponse withQuantity(BigDecimal scaledQuantity) {
        return new CookModeIngredientResponse(name, scaledQuantity, unit, preparation, optional);
    }
}