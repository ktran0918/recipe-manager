package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @PostMapping
    public ResponseEntity<RecipeResponse> createRecipe(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateRecipeRequest request) {
        requireHousehold(principal);
        RecipeResponse response = recipeService.createRecipe(
                principal.householdId(), principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // @RequestParam binds HTTP query parameters — ?q=pasta&page=2&per_page=10.
    // required = false means the param is optional; the method receives null if absent.
    // defaultValue provides a fallback when the param is missing.
    // name = "per_page" maps the snake_case query param to the camelCase Java parameter.
    // C# equivalent: [FromQuery] string? q, [FromQuery(Name = "per_page")] int perPage = 20
    @GetMapping
    public ResponseEntity<List<RecipeResponse>> listRecipes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {
        requireHousehold(principal);
        List<RecipeResponse> recipes = recipeService.listRecipes(
                principal.householdId(), q, page, perPage);
        return ResponseEntity.ok(recipes);
    }

    @GetMapping("/{id}/cook-mode")
    public ResponseEntity<CookModeResponse> getCookMode(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) BigDecimal servings) {
        requireHousehold(principal);
        CookModeResponse response = recipeService.getCookMode(id, principal.householdId(), servings);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipe(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) BigDecimal servings) {
        requireHousehold(principal);
        RecipeResponse recipe = recipeService.getRecipe(id, principal.householdId(), servings);
        return ResponseEntity.ok(recipe);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody UpdateRecipeRequest request) {
        requireHousehold(principal);
        RecipeResponse response = recipeService.updateRecipe(id, principal.householdId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        requireHousehold(principal);
        recipeService.deleteRecipe(id, principal.householdId());
        return ResponseEntity.noContent().build();
    }

    private static void requireHousehold(UserPrincipal principal) {
        if (principal.householdId() == null)
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a member of any household");
    }
}