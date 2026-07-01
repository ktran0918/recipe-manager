package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.RecipeIngredient;
import java.math.BigDecimal;
import java.util.UUID;

public record IngredientLineResponse(
        UUID id,
        String ingredientName,
        BigDecimal quantity,
        String unit,
        String preparation,
        boolean optional,
        String rawText,
        int sortOrder
) {
    public static IngredientLineResponse from(RecipeIngredient ri) {
        return new IngredientLineResponse(
                ri.getId(),
                ri.getIngredient().getName(),
                ri.getQuantity(),
                ri.getUnit(),
                ri.getPreparation(),
                ri.isOptional(),
                ri.getRawText(),
                ri.getSortOrder()
        );
    }

    // Returns a copy of this response with a different quantity — used by the scaling path.
    // C# equivalent: this with { Quantity = scaledQuantity }  (record copy-and-modify syntax)
    public IngredientLineResponse withQuantity(BigDecimal scaledQuantity) {
        return new IngredientLineResponse(
                id, ingredientName, scaledQuantity, unit, preparation, optional, rawText, sortOrder);
    }
}