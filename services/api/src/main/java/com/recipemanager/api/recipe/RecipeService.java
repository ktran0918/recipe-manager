package com.recipemanager.api.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RecipeService {

    RecipeResponse createRecipe(UUID householdId, UUID userId, CreateRecipeRequest request);

    // keyword is null when no ?q= param is supplied — service treats null as "no filter".
    List<RecipeResponse> listRecipes(UUID householdId, String keyword, int page, int perPage);

    // requestedServings is null when no ?servings= param is supplied — returns original quantities.
    RecipeResponse getRecipe(UUID id, UUID householdId, BigDecimal requestedServings);

    // requestedServings is null when no ?servings= param is supplied — defaults to recipe's own servings.
    CookModeResponse getCookMode(UUID id, UUID householdId, BigDecimal requestedServings);

    RecipeResponse updateRecipe(UUID id, UUID householdId, UpdateRecipeRequest request);

    void deleteRecipe(UUID id, UUID householdId);
}