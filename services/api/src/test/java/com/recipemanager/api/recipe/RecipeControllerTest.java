package com.recipemanager.api.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.config.SecurityConfig;
import com.recipemanager.api.domain.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecipeController.class)
@Import(SecurityConfig.class)
class RecipeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RecipeService recipeService;
    @MockBean private JwtService jwtService;

    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID HOUSEHOLD_ID = UUID.randomUUID();
    private static final UUID RECIPE_ID    = UUID.randomUUID();

    private static RequestPostProcessor asMember() {
        UserPrincipal principal = new UserPrincipal(USER_ID, HOUSEHOLD_ID, "member");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("member")));
        return authentication(auth);
    }

    // --- POST /recipes ---

    @Test
    void createRecipe_returnsCreated() throws Exception {
        CreateRecipeRequest request = new CreateRecipeRequest(
                "Garlic Pasta", null, null, null,
                BigDecimal.valueOf(2), 20, 10, "easy",
                new String[]{"weeknight"}, "Italian", null,
                List.of(new IngredientLineRequest(
                        "garlic", BigDecimal.valueOf(3), "clove", null, false, "3 cloves garlic")),
                List.of(new StepRequest("Boil pasta.")));

        when(recipeService.createRecipe(eq(HOUSEHOLD_ID), eq(USER_ID), any()))
                .thenReturn(recipeResponse("Garlic Pasta"));

        mockMvc.perform(post("/recipes").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Garlic Pasta"))
                .andExpect(jsonPath("$.id").exists());
    }

    // --- GET /recipes ---

    @Test
    void listRecipes_noFilter_returnsAll() throws Exception {
        when(recipeService.listRecipes(HOUSEHOLD_ID, null, 1, 20))
                .thenReturn(List.of(recipeResponse("Recipe One"), recipeResponse("Recipe Two")));

        mockMvc.perform(get("/recipes").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listRecipes_filtersByKeyword_passesKeywordToService() throws Exception {
        when(recipeService.listRecipes(HOUSEHOLD_ID, "pasta", 1, 20))
                .thenReturn(List.of(recipeResponse("Garlic Pasta")));

        mockMvc.perform(get("/recipes")
                        .param("q", "pasta")
                        .with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(recipeService).listRecipes(HOUSEHOLD_ID, "pasta", 1, 20);
    }

    // --- GET /recipes/{id} ---

    @Test
    void getRecipe_noServingsParam_returnsOriginalQuantities() throws Exception {
        when(recipeService.getRecipe(RECIPE_ID, HOUSEHOLD_ID, null))
                .thenReturn(recipeResponse("Garlic Pasta"));

        mockMvc.perform(get("/recipes/" + RECIPE_ID).with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Garlic Pasta"));
    }

    @Test
    void getRecipe_wrongHousehold_returns404() throws Exception {
        when(recipeService.getRecipe(RECIPE_ID, HOUSEHOLD_ID, null))
                .thenThrow(new EntityNotFoundException("Recipe not found"));

        mockMvc.perform(get("/recipes/" + RECIPE_ID).with(asMember()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRecipe_withServingsParam_passesServingsToService() throws Exception {
        when(recipeService.getRecipe(RECIPE_ID, HOUSEHOLD_ID, new BigDecimal("4")))
                .thenReturn(recipeResponse("Garlic Pasta"));

        mockMvc.perform(get("/recipes/" + RECIPE_ID)
                        .param("servings", "4")
                        .with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Garlic Pasta"));

        verify(recipeService).getRecipe(RECIPE_ID, HOUSEHOLD_ID, new BigDecimal("4"));
    }

    // --- PATCH /recipes/{id} ---

    @Test
    void updateRecipe_returnsUpdatedRecipe() throws Exception {
        UpdateRecipeRequest request = new UpdateRecipeRequest(
                "Updated Title", null, null, null, null, null, null, null, null, null, null);

        when(recipeService.updateRecipe(eq(RECIPE_ID), eq(HOUSEHOLD_ID), any()))
                .thenReturn(recipeResponse("Updated Title"));

        mockMvc.perform(patch("/recipes/" + RECIPE_ID).with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    void updateRecipe_wrongHousehold_returns404() throws Exception {
        when(recipeService.updateRecipe(eq(RECIPE_ID), eq(HOUSEHOLD_ID), any()))
                .thenThrow(new EntityNotFoundException("Recipe not found"));

        mockMvc.perform(patch("/recipes/" + RECIPE_ID).with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateRecipeRequest(
                                        "X", null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /recipes/{id} ---

    @Test
    void deleteRecipe_returns204() throws Exception {
        doNothing().when(recipeService).deleteRecipe(RECIPE_ID, HOUSEHOLD_ID);

        mockMvc.perform(delete("/recipes/" + RECIPE_ID).with(asMember()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRecipe_wrongHousehold_returns404() throws Exception {
        doThrow(new EntityNotFoundException("Recipe not found"))
                .when(recipeService).deleteRecipe(RECIPE_ID, HOUSEHOLD_ID);

        mockMvc.perform(delete("/recipes/" + RECIPE_ID).with(asMember()))
                .andExpect(status().isNotFound());
    }

    // Builds a minimal RecipeResponse for stubbing.
    private RecipeResponse recipeResponse(String title) {
        return new RecipeResponse(
                RECIPE_ID, HOUSEHOLD_ID, USER_ID, title,
                null, null, null,
                BigDecimal.valueOf(2), 20, 10, "easy",
                new String[]{"weeknight"}, "Italian", null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(), List.of());
    }
}