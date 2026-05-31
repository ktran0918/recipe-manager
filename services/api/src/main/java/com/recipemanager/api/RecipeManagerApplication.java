package com.recipemanager.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan combined.
// It tells Spring to scan this package and all sub-packages for components to wire up.
// C# equivalent: the top of Program.cs where you call builder.Build().Run().
@SpringBootApplication
public class RecipeManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipeManagerApplication.class, args);
    }
}
