import logging
from uuid import UUID

import asyncpg

from scraper.ingredients import normalize_ingredient_name
from scraper.recipe_schema import ParsedRecipe

logger = logging.getLogger(__name__)


async def write_recipe(
    pool: asyncpg.Pool,
    *,
    household_id: UUID,
    created_by: UUID,
    source_url: str,
    recipe: ParsedRecipe,
) -> UUID:
    """Writes the parsed recipe + ingredients + steps + substitutions in one
    transaction. See docs/stories.md EP2-05.
    """
    async with pool.acquire() as conn, conn.transaction():
        recipe_id: UUID = await conn.fetchval(
            """
            INSERT INTO recipes (household_id, created_by, title, description, source_url,
                                  servings, cook_time_minutes, prep_time_minutes, parsed_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, now())
            RETURNING id
            """,
            household_id,
            created_by,
            recipe.title,
            recipe.description,
            source_url,
            recipe.servings,
            recipe.cook_time_minutes,
            recipe.prep_time_minutes,
        )

        # Cache normalized_name -> ingredient_id within this transaction so the same
        # ingredient appearing in both the ingredient list and a substitution (e.g.
        # "garlic") only costs one round trip to the catalog, not two.
        ingredient_cache: dict[str, UUID] = {}

        for sort_order, ingredient in enumerate(recipe.ingredients):
            ingredient_id = await _resolve_ingredient_id(conn, ingredient.name, ingredient_cache)
            if ingredient_id is None:
                logger.warning("Skipping ingredient with unresolvable name: %r", ingredient.raw_text)
                continue
            await conn.execute(
                """
                INSERT INTO recipe_ingredients
                    (recipe_id, ingredient_id, quantity, unit, preparation, optional, sort_order, raw_text)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
                """,
                recipe_id,
                ingredient_id,
                _coerce_quantity(ingredient.quantity),
                ingredient.unit,
                ingredient.preparation,
                ingredient.optional,
                sort_order,
                ingredient.raw_text,
            )

        for step in recipe.steps:
            await conn.execute(
                "INSERT INTO recipe_steps (recipe_id, step_number, instruction) VALUES ($1, $2, $3)",
                recipe_id,
                step.step_number,
                step.instruction,
            )

        for substitution in recipe.substitutions or []:
            original_id = await _resolve_ingredient_id(conn, substitution.original_ingredient_name, ingredient_cache)
            substitute_id = await _resolve_ingredient_id(
                conn, substitution.substitute_ingredient_name, ingredient_cache
            )
            if original_id is None or substitute_id is None or original_id == substitute_id:
                logger.warning(
                    "Skipping unresolvable or self-referential substitution: %s -> %s",
                    substitution.original_ingredient_name,
                    substitution.substitute_ingredient_name,
                )
                continue
            await conn.execute(
                """
                INSERT INTO recipe_ingredient_substitutions
                    (recipe_id, original_ingredient_id, substitute_ingredient_id, conversion_ratio, notes, source)
                VALUES ($1, $2, $3, $4, $5, 'recipe')
                ON CONFLICT (recipe_id, original_ingredient_id, substitute_ingredient_id) DO NOTHING
                """,
                recipe_id,
                original_id,
                substitute_id,
                substitution.conversion_ratio,
                substitution.notes,
            )

    return recipe_id


async def _resolve_ingredient_id(
    conn: asyncpg.Connection, raw_name: str | None, cache: dict[str, UUID]
) -> UUID | None:
    normalized = normalize_ingredient_name(raw_name)
    if normalized is None or raw_name is None:
        return None
    if normalized in cache:
        return cache[normalized]

    # INSERT ... ON CONFLICT DO UPDATE ... RETURNING id is an atomic upsert — it avoids
    # a SELECT-then-INSERT race where two concurrent writers both miss the SELECT and
    # then collide on the INSERT (ingredients.normalized_name is UNIQUE). DO NOTHING
    # would not return the existing row's id on conflict; DO UPDATE with a no-op SET
    # does, which is why that's used here instead.
    ingredient_id: UUID = await conn.fetchval(
        """
        INSERT INTO ingredients (name, normalized_name)
        VALUES ($1, $2)
        ON CONFLICT (normalized_name) DO UPDATE SET name = ingredients.name
        RETURNING id
        """,
        raw_name.strip(),
        normalized,
    )
    cache[normalized] = ingredient_id
    return ingredient_id


def _coerce_quantity(quantity: float | str | None) -> float | None:
    if quantity is None:
        return None
    if isinstance(quantity, (int, float)):
        return float(quantity)
    # Guardrails' IngredientQuantitiesParseable already guarantees this parses —
    # this is just the type coercion for the NUMERIC column, not re-validation.
    return float(quantity)
