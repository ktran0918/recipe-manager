package com.recipemanager.api.recipe;

import com.recipemanager.api.domain.RecipeStep;
import java.util.UUID;

public record StepResponse(UUID id, int stepNumber, String instruction) {

    public static StepResponse from(RecipeStep step) {
        return new StepResponse(step.getId(), step.getStepNumber(), step.getInstruction());
    }
}