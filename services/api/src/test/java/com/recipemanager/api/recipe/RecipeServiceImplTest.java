package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.Recipe;
import com.recipemanager.api.repository.IngredientRepository;
import com.recipemanager.api.repository.RecipeIngredientRepository;
import com.recipemanager.api.repository.RecipeNutritionRepository;
import com.recipemanager.api.repository.RecipeRepository;
import com.recipemanager.api.repository.RecipeSearchRepository;
import com.recipemanager.api.repository.RecipeStepRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit test — no Spring context, no Elasticsearch, no Postgres. Verifies the
// Elasticsearch-vs-Postgres orchestration in listRecipes() directly against mocked
// repositories, the same way AuthServiceTest exercises AuthServiceImpl.
@ExtendWith(MockitoExtension.class)
class RecipeServiceImplTest {

    @Mock private RecipeRepository recipeRepository;
    @Mock private RecipeSearchRepository recipeSearchRepository;
    @Mock private RecipeIngredientRepository recipeIngredientRepository;
    @Mock private RecipeStepRepository recipeStepRepository;
    @Mock private IngredientRepository ingredientRepository;
    @Mock private RecipeNutritionRepository recipeNutritionRepository;

    @InjectMocks private RecipeServiceImpl recipeService;

    private static final UUID HOUSEHOLD_ID = UUID.randomUUID();

    private static Recipe recipeWithId(UUID id, String title) {
        Recipe recipe = new Recipe();
        ReflectionTestUtils.setField(recipe, "id", id);
        recipe.setHouseholdId(HOUSEHOLD_ID);
        recipe.setCreatedBy(UUID.randomUUID());
        recipe.setTitle(title);
        recipe.setServings(BigDecimal.ONE);
        return recipe;
    }

    private static RecipeDocument documentWithId(UUID id) {
        RecipeDocument doc = new RecipeDocument();
        ReflectionTestUtils.setField(doc, "id", id.toString());
        return doc;
    }

    @Test
    void listRecipes_noKeyword_queriesPostgresDirectly() {
        when(recipeRepository.findByHouseholdId(eq(HOUSEHOLD_ID), any()))
                .thenReturn(new PageImpl<>(List.of(recipeWithId(UUID.randomUUID(), "Garlic Pasta"))));

        List<RecipeResponse> result = recipeService.listRecipes(HOUSEHOLD_ID, null, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Garlic Pasta");
        verify(recipeSearchRepository, never()).searchByKeyword(any(), any(), any());
    }

    @Test
    void listRecipes_withKeyword_searchesElasticsearchThenHydratesFromPostgres() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        // Elasticsearch returns secondId ranked ABOVE firstId — the point of this test is
        // confirming that ranking survives the round trip back through Postgres, since
        // findAllById does not itself preserve the order of the ids passed in.
        Page<RecipeDocument> hits = new PageImpl<>(List.of(documentWithId(secondId), documentWithId(firstId)));
        when(recipeSearchRepository.searchByKeyword(eq(HOUSEHOLD_ID.toString()), eq("pasta"), any()))
                .thenReturn(hits);
        when(recipeRepository.findAllById(List.of(secondId, firstId)))
                .thenReturn(List.of(recipeWithId(firstId, "Garlic Pasta"), recipeWithId(secondId, "Shrimp Pasta")));

        List<RecipeResponse> result = recipeService.listRecipes(HOUSEHOLD_ID, "pasta", 1, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Shrimp Pasta");
        assertThat(result.get(1).title()).isEqualTo("Garlic Pasta");
        verify(recipeRepository, never()).findByHouseholdId(any(), any());
    }

    @Test
    void listRecipes_withKeyword_skipsSearchHitsDeletedFromPostgres() {
        UUID staleId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        Page<RecipeDocument> hits = new PageImpl<>(List.of(documentWithId(staleId), documentWithId(liveId)));
        when(recipeSearchRepository.searchByKeyword(eq(HOUSEHOLD_ID.toString()), eq("pasta"), any()))
                .thenReturn(hits);
        // staleId isn't in the Postgres result — simulates a recipe deleted after indexing.
        when(recipeRepository.findAllById(List.of(staleId, liveId)))
                .thenReturn(List.of(recipeWithId(liveId, "Garlic Pasta")));

        List<RecipeResponse> result = recipeService.listRecipes(HOUSEHOLD_ID, "pasta", 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Garlic Pasta");
    }
}
