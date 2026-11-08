from collections.abc import AsyncGenerator
from pathlib import Path
from uuid import UUID, uuid4

import asyncpg
import pytest
import pytest_asyncio
from testcontainers.community.postgres import PostgresContainer

from scraper.recipe_schema import ParsedIngredient, ParsedRecipe, ParsedStep, ParsedSubstitution
from scraper.recipe_writer import write_recipe

# A module-scoped async fixture (the pool, created once per container) needs the test
# functions that use it to run on that same module-scoped event loop — pytest-asyncio's
# default is a fresh loop per test function, which would make the pool's connections
# belong to a different loop than the test awaiting them.
pytestmark = pytest.mark.asyncio(loop_scope="module")

# Reuses the API service's real Flyway migrations rather than hand-duplicating the schema
# here — this is the single source of truth for the whole project (CLAUDE.md: "Flyway owns
# all schema changes"), and applying the real .sql files directly means this test can't
# silently drift from the actual schema the way a separately maintained fixture could.
# They're plain SQL with no Flyway-specific placeholder syntax, so asyncpg can run them as-is.
MIGRATIONS_DIR = Path(__file__).resolve().parents[2] / "api" / "src" / "main" / "resources" / "db" / "migration"

# Truncated before every test for isolation. write_recipe() opens its own pool.acquire() +
# transaction internally (it's written to take a Pool, matching its production call site in
# consumer.py), which rules out wrapping each test in one shared, rolled-back transaction the
# way the Java service's @DataJpaTest tests do — a second pool.acquire() on the same
# single-connection pool would just block waiting for the first to free up. Truncation gets
# the same per-test isolation without fighting the connection pool.
TABLES_TO_TRUNCATE = [
    "recipe_ingredient_substitutions",
    "recipe_steps",
    "recipe_ingredients",
    "recipes",
    "ingredients",
    "household_members",
    "households",
    "users",
]


@pytest.fixture(scope="module")
def postgres_container() -> AsyncGenerator[PostgresContainer, None]:
    with PostgresContainer("postgres:16") as postgres:
        yield postgres


@pytest_asyncio.fixture(scope="module", loop_scope="module")
async def pool(postgres_container: PostgresContainer) -> AsyncGenerator[asyncpg.Pool, None]:
    db_pool = await asyncpg.create_pool(
        host=postgres_container.get_container_host_ip(),
        port=int(postgres_container.get_exposed_port(5432)),
        user=postgres_container.username,
        password=postgres_container.password,
        database=postgres_container.dbname,
    )
    async with db_pool.acquire() as conn:
        for migration in sorted(MIGRATIONS_DIR.glob("V*.sql")):
            await conn.execute(migration.read_text())
    yield db_pool
    await db_pool.close()


@pytest_asyncio.fixture(autouse=True, loop_scope="module")
async def clean_tables(pool: asyncpg.Pool) -> None:
    async with pool.acquire() as conn:
        await conn.execute(f"TRUNCATE {', '.join(TABLES_TO_TRUNCATE)} RESTART IDENTITY CASCADE")


@pytest_asyncio.fixture(loop_scope="module")
async def household_and_user(pool: asyncpg.Pool) -> tuple[UUID, UUID]:
    async with pool.acquire() as conn:
        user_id: UUID = await conn.fetchval(
            """
            INSERT INTO users (email, display_name, oauth_provider, oauth_id)
            VALUES ($1, 'Test User', 'google', $2)
            RETURNING id
            """,
            f"test-{uuid4()}@example.com",
            str(uuid4()),
        )
        household_id: UUID = await conn.fetchval(
            "INSERT INTO households (name, invite_code) VALUES ('Test Household', $1) RETURNING id",
            str(uuid4())[:8],
        )
    return household_id, user_id


def _parsed_recipe(**overrides: object) -> ParsedRecipe:
    base: dict[str, object] = {
        "title": "Garlic Butter Pasta",
        "servings": 4,
        "cook_time_minutes": 20,
        "ingredients": [
            ParsedIngredient(raw_text="8 oz spaghetti", name="spaghetti", quantity=8, unit="oz"),
            ParsedIngredient(
                raw_text="4 cloves garlic, minced",
                name="garlic cloves",
                quantity=4,
                unit="clove",
                preparation="minced",
            ),
        ],
        "steps": [ParsedStep(step_number=1, instruction="Boil pasta.")],
    }
    base.update(overrides)
    return ParsedRecipe(**base)


async def test_write_recipe_creates_rows_in_all_tables(
    pool: asyncpg.Pool, household_and_user: tuple[UUID, UUID]
) -> None:
    household_id, user_id = household_and_user
    recipe = _parsed_recipe()

    recipe_id = await write_recipe(
        pool,
        household_id=household_id,
        created_by=user_id,
        source_url="https://example.com/garlic-pasta",
        recipe=recipe,
    )

    async with pool.acquire() as conn:
        recipe_row = await conn.fetchrow("SELECT * FROM recipes WHERE id = $1", recipe_id)
        assert recipe_row is not None
        assert recipe_row["title"] == "Garlic Butter Pasta"
        assert recipe_row["parsed_at"] is not None

        ingredient_rows = await conn.fetch(
            "SELECT * FROM recipe_ingredients WHERE recipe_id = $1 ORDER BY sort_order", recipe_id
        )
        assert len(ingredient_rows) == 2
        assert ingredient_rows[0]["raw_text"] == "8 oz spaghetti"
        # "garlic cloves" singularizes to "garlic clove" via inflect, not to the bare "garlic".
        garlic_ingredient = await conn.fetchrow(
            "SELECT * FROM ingredients WHERE id = $1", ingredient_rows[1]["ingredient_id"]
        )
        assert garlic_ingredient is not None
        assert garlic_ingredient["normalized_name"] == "garlic clove"

        step_rows = await conn.fetch("SELECT * FROM recipe_steps WHERE recipe_id = $1", recipe_id)
        assert len(step_rows) == 1
        assert step_rows[0]["instruction"] == "Boil pasta."


async def test_write_recipe_reuses_existing_ingredient_row(
    pool: asyncpg.Pool, household_and_user: tuple[UUID, UUID]
) -> None:
    household_id, user_id = household_and_user
    recipe1 = _parsed_recipe()
    recipe2 = _parsed_recipe(title="Garlic Shrimp")

    await write_recipe(
        pool, household_id=household_id, created_by=user_id, source_url="https://example.com/1", recipe=recipe1
    )
    await write_recipe(
        pool, household_id=household_id, created_by=user_id, source_url="https://example.com/2", recipe=recipe2
    )

    async with pool.acquire() as conn:
        count = await conn.fetchval("SELECT count(*) FROM ingredients WHERE normalized_name = 'spaghetti'")
        assert count == 1


async def test_write_recipe_persists_substitutions(
    pool: asyncpg.Pool, household_and_user: tuple[UUID, UUID]
) -> None:
    household_id, user_id = household_and_user
    recipe = _parsed_recipe(
        substitutions=[
            ParsedSubstitution(
                original_ingredient_name="sour cream",
                substitute_ingredient_name="Greek yogurt",
                conversion_ratio=1.0,
                notes="equal parts",
            )
        ]
    )

    recipe_id = await write_recipe(
        pool,
        household_id=household_id,
        created_by=user_id,
        source_url="https://example.com/garlic-pasta-sub",
        recipe=recipe,
    )

    async with pool.acquire() as conn:
        sub_rows = await conn.fetch("SELECT * FROM recipe_ingredient_substitutions WHERE recipe_id = $1", recipe_id)
        assert len(sub_rows) == 1
        assert sub_rows[0]["source"] == "recipe"
        assert sub_rows[0]["conversion_ratio"] == 1.0


async def test_write_recipe_skips_self_referential_substitution(
    pool: asyncpg.Pool, household_and_user: tuple[UUID, UUID]
) -> None:
    household_id, user_id = household_and_user
    # Both names normalize to the same ingredient — the DB CHECK constraint forbids this,
    # so write_recipe must skip it rather than let the whole transaction fail.
    recipe = _parsed_recipe(
        substitutions=[
            ParsedSubstitution(
                original_ingredient_name="Tomatoes",
                substitute_ingredient_name="tomato",
                conversion_ratio=1.0,
            )
        ]
    )

    recipe_id = await write_recipe(
        pool,
        household_id=household_id,
        created_by=user_id,
        source_url="https://example.com/garlic-pasta-self-sub",
        recipe=recipe,
    )

    async with pool.acquire() as conn:
        sub_rows = await conn.fetch("SELECT * FROM recipe_ingredient_substitutions WHERE recipe_id = $1", recipe_id)
        assert len(sub_rows) == 0
