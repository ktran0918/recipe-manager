package com.recipemanager.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // @ManyToOne is the owning side of the relationship — it holds the FK column.
    // C# equivalent: a navigation property with [ForeignKey("RecipeId")] on the entity class.
    // @JoinColumn names the FK column in this table.
    // FetchType.LAZY: the Recipe is not loaded when you fetch a RecipeIngredient unless you
    // explicitly access recipe.getTitle() etc. Prevents unintended full-graph loading.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    private String unit;

    private String preparation;

    @Column(nullable = false)
    private boolean optional;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    public UUID getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public Ingredient getIngredient() { return ingredient; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getPreparation() { return preparation; }
    public boolean isOptional() { return optional; }
    public int getSortOrder() { return sortOrder; }
    public String getRawText() { return rawText; }

    public void setRecipe(Recipe recipe) { this.recipe = recipe; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setPreparation(String preparation) { this.preparation = preparation; }
    public void setOptional(boolean optional) { this.optional = optional; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setRawText(String rawText) { this.rawText = rawText; }
}
