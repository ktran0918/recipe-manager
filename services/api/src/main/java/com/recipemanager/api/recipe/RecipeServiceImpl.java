package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.Ingredient;
import com.recipemanager.api.domain.Recipe;
import com.recipemanager.api.domain.RecipeIngredient;
import com.recipemanager.api.domain.RecipeStep;
import com.recipemanager.api.repository.IngredientRepository;
import com.recipemanager.api.repository.RecipeIngredientRepository;
import com.recipemanager.api.repository.RecipeRepository;
import com.recipemanager.api.repository.RecipeStepRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
// Everything runs in the class-level @Transactional — if any save fails,
// the whole operation rolls back, leaving no orphaned rows.
@Transactional
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final IngredientRepository ingredientRepository;

    public RecipeServiceImpl(RecipeRepository recipeRepository,
                             RecipeIngredientRepository recipeIngredientRepository,
                             RecipeStepRepository recipeStepRepository,
                             IngredientRepository ingredientRepository) {
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.recipeStepRepository = recipeStepRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Override
    public RecipeResponse createRecipe(UUID householdId, UUID userId, CreateRecipeRequest request) {
        Recipe recipe = new Recipe();
        recipe.setHouseholdId(householdId);
        recipe.setCreatedBy(userId);
        recipe.setTitle(request.title());
        recipe.setDescription(request.description());
        recipe.setSourceUrl(request.sourceUrl());
        recipe.setImageUrl(request.imageUrl());
        recipe.setServings(request.servings());
        recipe.setCookTimeMinutes(request.cookTimeMinutes());
        recipe.setPrepTimeMinutes(request.prepTimeMinutes());
        recipe.setComplexity(request.complexity());
        recipe.setOccasions(request.occasions());
        recipe.setCuisine(request.cuisine());
        recipe.setDietTags(request.dietTags());

        for (int i = 0; i < request.ingredients().size(); i++) {
            // Step 1 — For each IngredientLineRequest, normalize and resolve the Ingredient
            IngredientLineRequest line = request.ingredients().get(i);
            String normalized = line.ingredientName().trim().toLowerCase();
            Ingredient ingredient = ingredientRepository.findByNormalizedName(normalized)
                    .orElseGet(() -> {
                        Ingredient newIngredient = new Ingredient();
                        newIngredient.setName(line.ingredientName().trim());
                        newIngredient.setNormalizedName(normalized);
                        return ingredientRepository.save(newIngredient);
                   });

            // Step 2 — Build and save a RecipeIngredient for each line.
            //   Set sortOrder from the list index (0-based).
            RecipeIngredient ri = new RecipeIngredient();
            ri.setRecipe(recipe); // unsaved Recipe reference is fine here
            ri.setIngredient(ingredient);
            ri.setQuantity(line.quantity());
            ri.setUnit(line.unit());
            ri.setPreparation(line.preparation());
            ri.setOptional(line.optional());
            ri.setRawText(line.rawText());
            ri.setSortOrder(i);
            recipe.getIngredients().add(ri);
        }

        // Step 3 — Build and save a RecipeStep for each StepRequest.
        //   Set stepNumber from the list index + 1 (1-based).
        for (int i = 0; i < request.steps().size(); i++) {
            RecipeStep rs = new RecipeStep();
            rs.setRecipe(recipe);
            rs.setStepNumber(i + 1); // 1-based
            rs.setInstruction(request.steps().get(i).instruction());
            recipe.getSteps().add(rs);
        }

        // Persist the recipe and its nested ingredients + steps in one transaction
        // and return
        return RecipeResponse.from(recipeRepository.save(recipe));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeResponse> listRecipes(UUID householdId, String keyword, int page, int perPage) {
        // Spring Data pagination uses PageRequest.of(pageIndex, size) where pageIndex is 0-based.
        // Our API uses 1-based page numbers, so subtract 1:
        PageRequest pageable = PageRequest.of(page - 1, perPage);

        if (StringUtils.hasText(keyword)) {
            return recipeRepository.findByHouseholdIdAndKeyword(householdId, keyword, pageable)
                    .stream().map(RecipeResponse::from).toList();
        }
        return recipeRepository.findByHouseholdId(householdId, pageable)
                .stream().map(RecipeResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(UUID id, UUID householdId, BigDecimal requestedServings) {
        Recipe recipe = recipeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe not found"));

        if (requestedServings == null) {
            return RecipeResponse.from(recipe);
        }

        // Scale each ingredient's quantity to requestedServings.
        //
        // Java's BigDecimal.divide() requires an explicit scale and RoundingMode —
        // unlike C#'s decimal operator, it throws ArithmeticException on non-terminating results:
        BigDecimal scaleFactor = requestedServings.divide(recipe.getServings(), 6, RoundingMode.HALF_UP);
        // For each RecipeIngredient, apply the factor and round to 2 decimal places:
        List<IngredientLineResponse> scaledIngredients = recipe.getIngredients().stream().map(ri -> {
            BigDecimal scaled = ri.getQuantity() != null
                    ? ri.getQuantity().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP)
                    : null;
            return IngredientLineResponse.from(ri).withQuantity(scaled);
        }).toList();

        return RecipeResponse.from(recipe, scaledIngredients);
    }

    @Override
    public RecipeResponse updateRecipe(UUID id, UUID householdId, UpdateRecipeRequest request) {
        Recipe recipe = recipeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe not found"));

        if (request.title() != null) recipe.setTitle(request.title());
        if (request.description() != null) recipe.setDescription(request.description());
        if (request.sourceUrl() != null) recipe.setSourceUrl(request.sourceUrl());
        if (request.imageUrl() != null) recipe.setImageUrl(request.imageUrl());
        if (request.servings() != null) recipe.setServings(request.servings());
        if (request.cookTimeMinutes() != null) recipe.setCookTimeMinutes(request.cookTimeMinutes());
        if (request.prepTimeMinutes() != null) recipe.setPrepTimeMinutes(request.prepTimeMinutes());
        if (request.complexity() != null) recipe.setComplexity(request.complexity());
        if (request.occasions() != null) recipe.setOccasions(request.occasions());
        if (request.cuisine() != null) recipe.setCuisine(request.cuisine());
        if (request.dietTags() != null) recipe.setDietTags(request.dietTags());

        return RecipeResponse.from(recipeRepository.save(recipe));
    }

    @Override
    public void deleteRecipe(UUID id, UUID householdId) {
        Recipe recipe = recipeRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe not found"));
        // CascadeType.ALL on Recipe.ingredients and Recipe.steps propagates the delete.
        recipeRepository.delete(recipe);
    }
}