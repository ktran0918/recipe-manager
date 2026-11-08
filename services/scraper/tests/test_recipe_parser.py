import json
from unittest.mock import AsyncMock

import pytest

from scraper.errors import ParseError
from scraper.recipe_parser import parse_recipe


def _valid_recipe_json(**overrides: object) -> str:
    base = {
        "title": "Garlic Butter Pasta",
        "servings": 4,
        "cook_time_minutes": 20,
        "ingredients": [
            {"raw_text": "8 oz spaghetti", "name": "spaghetti", "quantity": 8, "unit": "oz"},
        ],
        "steps": [{"step_number": 1, "instruction": "Boil pasta."}],
        "substitutions": [],
    }
    base.update(overrides)
    return json.dumps(base)


def _invalid_recipe_json() -> str:
    # servings=0 fails Guardrails' PositiveNumber check every time it's parsed.
    return _valid_recipe_json(servings=0)


async def test_parse_recipe_succeeds_on_local_first_attempt(monkeypatch):
    local = AsyncMock(return_value=_valid_recipe_json())
    cloud = AsyncMock()
    monkeypatch.setattr("scraper.recipe_parser._parse_local", local)
    monkeypatch.setattr("scraper.recipe_parser._parse_cloud", cloud)

    recipe = await parse_recipe("some article text")

    assert recipe.title == "Garlic Butter Pasta"
    assert recipe.ingredients[0].name == "spaghetti"
    local.assert_awaited_once()
    cloud.assert_not_called()


async def test_parse_recipe_includes_substitutions_when_present(monkeypatch):
    substitutions = [
        {
            "original_ingredient_name": "sour cream",
            "substitute_ingredient_name": "Greek yogurt",
            "conversion_ratio": 1.0,
            "notes": "equal parts",
        }
    ]
    local = AsyncMock(return_value=_valid_recipe_json(substitutions=substitutions))
    monkeypatch.setattr("scraper.recipe_parser._parse_local", local)
    monkeypatch.setattr("scraper.recipe_parser._parse_cloud", AsyncMock())

    recipe = await parse_recipe("some article text")

    assert recipe.substitutions is not None
    assert recipe.substitutions[0].substitute_ingredient_name == "Greek yogurt"


async def test_parse_recipe_retries_locally_once_on_validation_failure(monkeypatch):
    # First local attempt is invalid; second (the retry) succeeds — escalation never happens.
    local = AsyncMock(side_effect=[_invalid_recipe_json(), _valid_recipe_json()])
    cloud = AsyncMock()
    monkeypatch.setattr("scraper.recipe_parser._parse_local", local)
    monkeypatch.setattr("scraper.recipe_parser._parse_cloud", cloud)

    recipe = await parse_recipe("some article text")

    assert recipe.title == "Garlic Butter Pasta"
    assert local.await_count == 2
    # The retry call must include the correction note so the model knows what to fix.
    assert local.await_args_list[1].kwargs["correction_note"]
    cloud.assert_not_called()


async def test_parse_recipe_escalates_to_cloud_after_two_local_failures(monkeypatch):
    local = AsyncMock(side_effect=[_invalid_recipe_json(), _invalid_recipe_json()])
    cloud = AsyncMock(return_value=_valid_recipe_json())
    monkeypatch.setattr("scraper.recipe_parser._parse_local", local)
    monkeypatch.setattr("scraper.recipe_parser._parse_cloud", cloud)

    recipe = await parse_recipe("some article text")

    assert recipe.title == "Garlic Butter Pasta"
    assert local.await_count == 2
    cloud.assert_awaited_once()


async def test_parse_recipe_raises_parse_error_when_both_tiers_fail(monkeypatch):
    local = AsyncMock(side_effect=[_invalid_recipe_json(), _invalid_recipe_json()])
    cloud = AsyncMock(return_value=_invalid_recipe_json())
    monkeypatch.setattr("scraper.recipe_parser._parse_local", local)
    monkeypatch.setattr("scraper.recipe_parser._parse_cloud", cloud)

    with pytest.raises(ParseError):
        await parse_recipe("some article text")

    cloud.assert_awaited_once()
