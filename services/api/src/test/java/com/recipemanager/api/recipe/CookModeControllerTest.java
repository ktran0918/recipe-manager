package com.recipemanager.api.recipe;

import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.config.SecurityConfig;
import com.recipemanager.api.domain.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecipeController.class)
@Import(SecurityConfig.class)
class CookModeControllerTest {

    @Autowired private MockMvc mockMvc;

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

    @Test
    void getCookMode_returnsCondensedView() throws Exception {
        when(recipeService.getCookMode(RECIPE_ID, HOUSEHOLD_ID, null))
                .thenReturn(cookModeResponse());

        mockMvc.perform(get("/recipes/" + RECIPE_ID + "/cook-mode").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Garlic Pasta"))
                .andExpect(jsonPath("$.directions[0]").value("Boil pasta."))
                .andExpect(jsonPath("$.nutrition").isEmpty());
    }

    @Test
    void getCookMode_excludesNonEssentialFields() throws Exception {
        when(recipeService.getCookMode(RECIPE_ID, HOUSEHOLD_ID, null))
                .thenReturn(cookModeResponse());

        mockMvc.perform(get("/recipes/" + RECIPE_ID + "/cook-mode").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.image_url").doesNotExist())
                .andExpect(jsonPath("$.occasions").doesNotExist())
                .andExpect(jsonPath("$.cuisine").doesNotExist())
                .andExpect(jsonPath("$.diet_tags").doesNotExist())
                .andExpect(jsonPath("$.complexity").doesNotExist());
    }

    @Test
    void getCookMode_withServings_passesServingsToService() throws Exception {
        when(recipeService.getCookMode(RECIPE_ID, HOUSEHOLD_ID, new BigDecimal("4")))
                .thenReturn(cookModeResponse());

        mockMvc.perform(get("/recipes/" + RECIPE_ID + "/cook-mode")
                        .param("servings", "4")
                        .with(asMember()))
                .andExpect(status().isOk());

        verify(recipeService).getCookMode(RECIPE_ID, HOUSEHOLD_ID, new BigDecimal("4"));
    }

    @Test
    void getCookMode_noServings_passesNullToService() throws Exception {
        when(recipeService.getCookMode(RECIPE_ID, HOUSEHOLD_ID, null))
                .thenReturn(cookModeResponse());

        mockMvc.perform(get("/recipes/" + RECIPE_ID + "/cook-mode").with(asMember()))
                .andExpect(status().isOk());

        verify(recipeService).getCookMode(RECIPE_ID, HOUSEHOLD_ID, null);
    }

    @Test
    void getCookMode_wrongHousehold_returns404() throws Exception {
        when(recipeService.getCookMode(RECIPE_ID, HOUSEHOLD_ID, null))
                .thenThrow(new EntityNotFoundException("Recipe not found"));

        mockMvc.perform(get("/recipes/" + RECIPE_ID + "/cook-mode").with(asMember()))
                .andExpect(status().isNotFound());
    }

    private CookModeResponse cookModeResponse() {
        return new CookModeResponse(
                RECIPE_ID, "Garlic Pasta",
                10, 20, BigDecimal.valueOf(2),
                List.of(new CookModeIngredientResponse(
                        "garlic", BigDecimal.valueOf(3), "clove", null, false)),
                List.of("Boil pasta.", "Add garlic."),
                null,
                null);
    }
}