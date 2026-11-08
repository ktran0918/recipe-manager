import json
import logging
from typing import Any

from guardrails.errors import ValidationError as GuardrailsValidationError
from ollama import AsyncClient as OllamaAsyncClient

from scraper.config import settings
from scraper.errors import ParseError
from scraper.recipe_schema import ParsedRecipe
from scraper.recipe_validators import build_recipe_guard

logger = logging.getLogger(__name__)

RECIPE_PARSER_SYSTEM_PROMPT = """You are a precise recipe data extractor. Given the text of a \
recipe article, extract structured data matching the exact schema provided.

Rules:
- title: the recipe's name, cleaned of site branding (e.g. "The Best Garlic Pasta Recipe | \
Serious Eats" becomes "The Best Garlic Pasta").
- servings: a plain number. If the recipe gives a range (e.g. "serves 4-6"), use the lower bound.
- For each ingredient: raw_text is the ingredient line exactly as written; name is the clean \
ingredient name alone, with no quantity, unit, or preparation; quantity is a plain number, or \
null if the recipe gives no amount (e.g. "salt to taste"); unit is the unit of measure if any \
(e.g. "cup", "g", "clove"); preparation is any prep instruction (e.g. "minced", "chopped"), kept \
separate from name.
- steps are numbered in the order the recipe lists them, starting at 1.
- substitutions: only include ones the recipe text itself suggests (e.g. "you can use Greek \
yogurt instead of sour cream"). Leave the array empty if the recipe doesn't suggest any.
- Do not invent information that isn't present in the text. If a field is genuinely absent, use \
null rather than guessing.
"""

RECIPE_EXTRACTION_TOOL_NAME = "extract_recipe"


def _recipe_json_schema() -> dict[str, Any]:
    return ParsedRecipe.model_json_schema()


async def _parse_local(article_text: str, schema: dict[str, Any], correction_note: str | None = None) -> str:
    client = OllamaAsyncClient(host=settings.ollama_url)
    user_content = article_text
    if correction_note:
        # Guardrails rejected the first attempt — tell the model exactly what was wrong
        # rather than just re-asking blind, so the retry has a real chance of succeeding.
        user_content = (
            f"{article_text}\n\n---\nYour previous extraction was invalid: {correction_note}\n"
            "Correct the issue and extract the recipe again."
        )
    response = await client.chat(
        model=settings.ollama_parse_model,
        messages=[
            {"role": "system", "content": RECIPE_PARSER_SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ],
        format=schema,
    )
    return response.message.content or ""


async def _parse_cloud(article_text: str, schema: dict[str, Any]) -> str:
    # Imported lazily — the anthropic client is only constructed on the escalation
    # path, which the large majority of parses never reach.
    from anthropic import AsyncAnthropic

    client = AsyncAnthropic(api_key=settings.anthropic_api_key)
    response = await client.messages.create(
        model=settings.cloud_parse_model,
        max_tokens=4096,
        system=RECIPE_PARSER_SYSTEM_PROMPT,
        messages=[{"role": "user", "content": article_text}],
        tools=[
            {
                "name": RECIPE_EXTRACTION_TOOL_NAME,
                "description": "Extract structured recipe data from article text.",
                "input_schema": schema,
            }
        ],
        tool_choice={"type": "tool", "name": RECIPE_EXTRACTION_TOOL_NAME},
    )
    for block in response.content:
        if block.type == "tool_use":
            return json.dumps(block.input)
    raise ParseError("Claude response contained no tool_use block")


async def parse_recipe(article_text: str) -> ParsedRecipe:
    """Local-first recipe extraction with cloud escalation. See
    docs/adr/004-llm-recipe-parsing.md for the full pipeline and rationale.
    """
    guard = build_recipe_guard()
    schema = _recipe_json_schema()

    raw = await _parse_local(article_text, schema)
    try:
        outcome = guard.parse(llm_output=raw)
        return ParsedRecipe.model_validate(outcome.validated_output)
    except GuardrailsValidationError as first_error:
        # Python deletes the `as` binding when the except block exits, so the message
        # must be captured here — referencing first_error below would raise NameError.
        first_error_message = str(first_error)
        logger.warning("Local parse failed Guardrails validation, retrying locally once: %s", first_error_message)

    raw = await _parse_local(article_text, schema, correction_note=first_error_message)
    try:
        outcome = guard.parse(llm_output=raw)
        return ParsedRecipe.model_validate(outcome.validated_output)
    except GuardrailsValidationError as second_error:
        logger.warning(
            "Local retry also failed Guardrails validation, escalating to %s: %s",
            settings.cloud_parse_model,
            second_error,
        )

    raw = await _parse_cloud(article_text, schema)
    try:
        outcome = guard.parse(llm_output=raw)
        return ParsedRecipe.model_validate(outcome.validated_output)
    except GuardrailsValidationError as final_error:
        raise ParseError(
            f"Recipe parse failed Guardrails validation on both local and cloud tiers: {final_error}"
        ) from final_error
