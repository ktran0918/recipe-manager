from pydantic import BaseModel


class ParsedIngredient(BaseModel):
    raw_text: str
    name: str
    # Quantity is typed loosely (not just float | None) because a local model under
    # schema-constrained decoding can still be told "to taste" means null, but a
    # cloud tool-call or an imperfectly-constrained local run may emit a non-numeric
    # string (e.g. "a pinch") — RecipeValidators.IngredientQuantitiesParseable is the
    # real enforcement; the type here just has to not reject that case outright.
    quantity: float | str | None = None
    unit: str | None = None
    preparation: str | None = None
    optional: bool = False


class ParsedStep(BaseModel):
    step_number: int
    instruction: str


class ParsedSubstitution(BaseModel):
    original_ingredient_name: str
    substitute_ingredient_name: str
    conversion_ratio: float = 1.0
    notes: str | None = None


class ParsedRecipe(BaseModel):
    title: str
    description: str | None = None
    servings: float
    cook_time_minutes: int | None = None
    prep_time_minutes: int | None = None
    ingredients: list[ParsedIngredient]
    steps: list[ParsedStep]
    substitutions: list[ParsedSubstitution] | None = None
