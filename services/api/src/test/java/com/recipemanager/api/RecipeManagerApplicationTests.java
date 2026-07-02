package com.recipemanager.api;

import com.recipemanager.api.repository.HouseholdMemberRepository;
import com.recipemanager.api.repository.HouseholdRepository;
import com.recipemanager.api.repository.IngredientRepository;
import com.recipemanager.api.repository.RecipeIngredientRepository;
import com.recipemanager.api.repository.RecipeNutritionRepository;
import com.recipemanager.api.repository.RecipeRepository;
import com.recipemanager.api.repository.RecipeStepRepository;
import com.recipemanager.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

// Loads the full Spring application context.
// C# equivalent: WebApplicationFactory<Program> in an ASP.NET Core integration test.
//
// @ActiveProfiles("test") activates application-test.yml, which disables infra
// auto-configuration so this test passes without running containers.
// Integration tests that need a real DB use Testcontainers and skip this profile.
@SpringBootTest
@ActiveProfiles("test")
class RecipeManagerApplicationTests {

    // application-test.yml excludes DataSourceAutoConfiguration/HibernateJpaAutoConfiguration,
    // so Spring Data JPA never creates repository beans. HouseholdServiceImpl constructor-injects
    // these three, so without mocks the context fails with NoSuchBeanDefinitionException.
    // As more @Service classes pick up repository dependencies, they'll need @MockBean entries
    // here too — that's the accepted cost of keeping this smoke test infra-free.
    @MockBean private HouseholdRepository householdRepository;
    @MockBean private HouseholdMemberRepository householdMemberRepository;
    @MockBean private UserRepository userRepository;
    @MockBean private RecipeRepository recipeRepository;
    @MockBean private RecipeIngredientRepository recipeIngredientRepository;
    @MockBean private RecipeStepRepository recipeStepRepository;
    @MockBean private IngredientRepository ingredientRepository;
    @MockBean private RecipeNutritionRepository recipeNutritionRepository;

    // application-test.yml also excludes RedisAutoConfiguration, so AuthServiceImpl's
    // StringRedisTemplate dependency needs the same treatment.
    @MockBean private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
        // Spring Boot fails the test if the application context cannot start.
        // No assertions needed — a successful context startup is the assertion.
    }
}
