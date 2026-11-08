from typing import Any

from scraper.guardrails_setup import disable_guardrails_telemetry

disable_guardrails_telemetry()

from guardrails import Guard  # noqa: E402 — must follow the telemetry opt-out above
from guardrails.validator_base import (  # noqa: E402
    FailResult,
    PassResult,
    ValidationResult,
    Validator,
    register_validator,
)

from scraper.recipe_schema import ParsedRecipe  # noqa: E402

# guardrails-ai ships no type stubs, so `Validator` resolves to Any — mypy flags
# subclassing it as an error even though this is the library's documented API.
# The ignore comment on each class below is the targeted fix for that, not a real bug.


@register_validator(name="positive-number", data_type="float")
class PositiveNumber(Validator):  # type: ignore[misc]
    def _validate(self, value: object, metadata: dict[str, Any]) -> ValidationResult:
        if value is None or not isinstance(value, (int, float)) or value <= 0:
            return FailResult(error_message=f"must be a number > 0, got {value!r}")
        return PassResult()


@register_validator(name="non-negative-integer", data_type="integer")
class NonNegativeInteger(Validator):  # type: ignore[misc]
    def _validate(self, value: object, metadata: dict[str, Any]) -> ValidationResult:
        if value is not None and isinstance(value, (int, float)) and value < 0:
            return FailResult(error_message=f"must be >= 0, got {value!r}")
        return PassResult()


@register_validator(name="non-empty-list", data_type="list")
class NonEmptyList(Validator):  # type: ignore[misc]
    def _validate(self, value: object, metadata: dict[str, Any]) -> ValidationResult:
        if not value:
            return FailResult(error_message="must contain at least one item")
        return PassResult()


@register_validator(name="valid-ingredient-list", data_type="list")
class ValidIngredientList(Validator):  # type: ignore[misc]
    """Combines the non-empty and quantity-parseable checks for $.ingredients.

    Guardrails keys its validator map by JSONPath and keeps only the last
    validator registered per path — registering NonEmptyList and a separate
    quantities check on the same "$.ingredients" path silently drops the first
    one, so both checks live in a single validator here instead.
    """

    def _validate(self, value: object, metadata: dict[str, Any]) -> ValidationResult:
        items = value if isinstance(value, list) else []
        if not items:
            return FailResult(error_message="must contain at least one item")
        for i, ingredient in enumerate(items):
            quantity = ingredient.get("quantity") if isinstance(ingredient, dict) else None
            if quantity is None or isinstance(quantity, (int, float)):
                continue
            try:
                float(quantity)
            except (TypeError, ValueError):
                return FailResult(error_message=f"ingredient {i} has a non-numeric quantity: {quantity!r}")
        return PassResult()


def build_recipe_guard() -> Guard:
    guard = Guard.for_pydantic(output_class=ParsedRecipe)
    guard.use(PositiveNumber(on_fail="exception"), on="$.servings")
    guard.use(NonNegativeInteger(on_fail="exception"), on="$.cook_time_minutes")
    guard.use(ValidIngredientList(on_fail="exception"), on="$.ingredients")
    guard.use(NonEmptyList(on_fail="exception"), on="$.steps")
    return guard
