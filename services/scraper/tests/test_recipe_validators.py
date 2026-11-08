import json

import pytest
from guardrails.errors import ValidationError

from scraper.recipe_validators import build_recipe_guard


def _recipe_json(**overrides: object) -> str:
    base = {
        "title": "Garlic Butter Pasta",
        "servings": 4,
        "cook_time_minutes": 20,
        "ingredients": [
            {"raw_text": "8 oz spaghetti", "name": "spaghetti", "quantity": 8, "unit": "oz"},
        ],
        "steps": [{"step_number": 1, "instruction": "Boil pasta."}],
    }
    base.update(overrides)
    return json.dumps(base)


def test_valid_recipe_passes() -> None:
    guard = build_recipe_guard()
    outcome = guard.parse(llm_output=_recipe_json())
    assert outcome.validation_passed
    assert outcome.validated_output["title"] == "Garlic Butter Pasta"


def test_servings_zero_rejected() -> None:
    guard = build_recipe_guard()
    with pytest.raises(ValidationError):
        guard.parse(llm_output=_recipe_json(servings=0))


def test_negative_cook_time_rejected() -> None:
    guard = build_recipe_guard()
    with pytest.raises(ValidationError):
        guard.parse(llm_output=_recipe_json(cook_time_minutes=-5))


def test_null_cook_time_accepted() -> None:
    guard = build_recipe_guard()
    outcome = guard.parse(llm_output=_recipe_json(cook_time_minutes=None))
    assert outcome.validation_passed


def test_empty_ingredients_rejected() -> None:
    guard = build_recipe_guard()
    with pytest.raises(ValidationError):
        guard.parse(llm_output=_recipe_json(ingredients=[]))


def test_empty_steps_rejected() -> None:
    guard = build_recipe_guard()
    with pytest.raises(ValidationError):
        guard.parse(llm_output=_recipe_json(steps=[]))


def test_non_numeric_quantity_rejected() -> None:
    guard = build_recipe_guard()
    bad_ingredients = [{"raw_text": "a pinch of salt", "name": "salt", "quantity": "a pinch"}]
    with pytest.raises(ValidationError):
        guard.parse(llm_output=_recipe_json(ingredients=bad_ingredients))


def test_null_quantity_accepted() -> None:
    guard = build_recipe_guard()
    ingredients = [{"raw_text": "salt to taste", "name": "salt", "quantity": None}]
    outcome = guard.parse(llm_output=_recipe_json(ingredients=ingredients))
    assert outcome.validation_passed
