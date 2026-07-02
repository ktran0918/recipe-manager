package com.recipemanager.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recipe_nutrition")
public class RecipeNutrition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Owning side of the one-to-one relationship — holds the recipe_id FK column.
    // @OneToOne enforces that only one RecipeNutrition can reference any given Recipe.
    // C# equivalent: [ForeignKey("RecipeId")] public Recipe Recipe { get; set; }
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(precision = 8, scale = 1)
    private BigDecimal calories;

    @Column(name = "protein_g", precision = 8, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbs_g", precision = 8, scale = 2)
    private BigDecimal carbsG;

    @Column(name = "fat_g", precision = 8, scale = 2)
    private BigDecimal fatG;

    @Column(name = "fiber_g", precision = 8, scale = 2)
    private BigDecimal fiberG;

    @Column(name = "sodium_mg", precision = 8, scale = 1)
    private BigDecimal sodiumMg;

    @Column(name = "serving_size_label")
    private String servingSizeLabel;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public BigDecimal getCalories() { return calories; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getCarbsG() { return carbsG; }
    public BigDecimal getFatG() { return fatG; }
    public BigDecimal getFiberG() { return fiberG; }
    public BigDecimal getSodiumMg() { return sodiumMg; }
    public String getServingSizeLabel() { return servingSizeLabel; }
}