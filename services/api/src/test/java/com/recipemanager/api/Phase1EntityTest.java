package com.recipemanager.api;

import com.recipemanager.api.domain.*;
import com.recipemanager.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest loads only the JPA slice — no web layer, no security, no messaging.
// C# equivalent: a test that creates a fresh DbContext backed by a test database.
//
// @AutoConfigureTestDatabase(replace=NONE) prevents Spring from substituting the real
// datasource with an in-memory H2 database. We want a real PostgreSQL instance because
// we use PostgreSQL-specific types (UUID, text[], TIMESTAMPTZ).
//
// @Testcontainers + @Container spin up a real PostgreSQL Docker container per test class.
// C# equivalent: using Testcontainers.PostgreSql in an xUnit test with IAsyncLifetime.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class Phase1EntityTest {

    // The static container is shared across all tests in this class. Testcontainers
    // starts it once before the first test and stops it after the last.
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("recipe_manager_test")
            .withUsername("test")
            .withPassword("test");

    // @DynamicPropertySource wires the running container's connection details into
    // Spring's property system before the application context starts.
    // C# equivalent: overriding WebApplicationFactory.ConfigureWebHost to swap the connection string.
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // EP1-03 (Flyway migrations) isn't done yet — let Hibernate generate the schema
        // from entities directly. Switch back to "validate" once Flyway migrations exist.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired UserRepository userRepository;
    @Autowired HouseholdRepository householdRepository;
    @Autowired HouseholdMemberRepository householdMemberRepository;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired RecipeIngredientRepository recipeIngredientRepository;
    @Autowired RecipeStepRepository recipeStepRepository;

    // Shared fixtures — built once per test in @BeforeEach so each test starts clean.
    private User user;
    private Household household;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setOauthProvider("google");
        user.setOauthId("google-id-123");
        user = userRepository.save(user);

        household = new Household();
        household.setName("Test Household");
        household.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        household = householdRepository.save(household);

        ingredient = new Ingredient();
        ingredient.setName("Garlic");
        ingredient.setNormalizedName("garlic");
        ingredient.setCategory("produce");
        ingredient = ingredientRepository.save(ingredient);
    }

    @Test
    void saveAndRetrieveUser() {
        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Test User");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    void saveAndRetrieveHouseholdMember() {
        HouseholdMember member = new HouseholdMember(household, user, "owner");
        householdMemberRepository.save(member);

        List<HouseholdMember> members = householdMemberRepository.findByHousehold_Id(household.getId());

        assertThat(members).hasSize(1);
        assertThat(members.getFirst().getRole()).isEqualTo("owner");
        assertThat(members.getFirst().getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void saveRecipeWithIngredientsAndSteps() {
        Recipe recipe = new Recipe();
        recipe.setHouseholdId(household.getId());
        recipe.setCreatedBy(user.getId());
        recipe.setTitle("Garlic Pasta");
        recipe.setServings(new BigDecimal("2"));
        recipe.setOccasions(new String[]{"weeknight"});
        recipe = recipeRepository.save(recipe);

        RecipeIngredient ri = new RecipeIngredient();
        ri.setRecipe(recipe);
        ri.setIngredient(ingredient);
        ri.setQuantity(new BigDecimal("3"));
        ri.setUnit("clove");
        ri.setRawText("3 cloves garlic, minced");
        ri.setSortOrder(0);
        recipeIngredientRepository.save(ri);

        RecipeStep step = new RecipeStep();
        step.setRecipe(recipe);
        step.setStepNumber(1);
        step.setInstruction("Mince the garlic.");
        recipeStepRepository.save(step);

        List<RecipeIngredient> ingredients = recipeIngredientRepository.findByRecipe_IdOrderBySortOrderAsc(recipe.getId());
        assertThat(ingredients).hasSize(1);
        assertThat(ingredients.getFirst().getUnit()).isEqualTo("clove");

        List<RecipeStep> steps = recipeStepRepository.findByRecipe_IdOrderByStepNumberAsc(recipe.getId());
        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().getInstruction()).contains("garlic");
    }

    @Test
    void findRecipesByHouseholdId() {
        Recipe r1 = new Recipe();
        r1.setHouseholdId(household.getId());
        r1.setCreatedBy(user.getId());
        r1.setTitle("Recipe One");
        r1.setServings(BigDecimal.ONE);
        recipeRepository.save(r1);

        Recipe r2 = new Recipe();
        r2.setHouseholdId(household.getId());
        r2.setCreatedBy(user.getId());
        r2.setTitle("Recipe Two");
        r2.setServings(BigDecimal.ONE);
        recipeRepository.save(r2);

        List<Recipe> recipes = recipeRepository.findByHouseholdId(household.getId());
        assertThat(recipes).hasSize(2);
        assertThat(recipes).allMatch(r -> r.getHouseholdId().equals(household.getId()));

        List<Recipe> recipesFromRandom = recipeRepository.findByHouseholdId(UUID.randomUUID());
        assertThat(recipesFromRandom).isEmpty();
    }
}
