package com.recipemanager.api.repository;

import com.recipemanager.api.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    Optional<Ingredient> findByNormalizedName(String normalizedName);

    Optional<Ingredient> findByBarcode(String barcode);
}
