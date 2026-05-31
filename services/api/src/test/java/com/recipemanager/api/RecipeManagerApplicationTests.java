package com.recipemanager.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void contextLoads() {
        // Spring Boot fails the test if the application context cannot start.
        // No assertions needed — a successful context startup is the assertion.
    }
}
