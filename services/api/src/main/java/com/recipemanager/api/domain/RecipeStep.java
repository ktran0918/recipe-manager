package com.recipemanager.api.domain;

import jakarta.persistence.*;
import java.util.UUID;

// Note: schema.md lists both "id UUID PRIMARY KEY" and "PRIMARY KEY (recipe_id, step_number)"
// on this table — that's a documentation inconsistency (a table can only have one PK).
// We use 'id' as the single primary key and enforce uniqueness on (recipe_id, step_number)
// via @UniqueConstraint, which matches the intent expressed in the schema comment.
@Entity
@Table(
    name = "recipe_steps",
    uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_id", "step_number"})
)
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(nullable = false)
    private String instruction;

    public UUID getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public int getStepNumber() { return stepNumber; }
    public String getInstruction() { return instruction; }

    public void setRecipe(Recipe recipe) { this.recipe = recipe; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
}
