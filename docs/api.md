# API Reference

Base URL: `http://localhost:8000/api/v1`  
Auth: Bearer JWT in `Authorization` header (except `/auth/*` routes)  
GraphQL: `http://localhost:8000/graphql`

---

## Authentication

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/auth/google` | Redirect to Google OAuth consent screen |
| `GET` | `/auth/google/callback` | OAuth callback — issues JWT + refresh token |
| `POST` | `/auth/refresh` | Exchange refresh token for new access JWT |
| `POST` | `/auth/logout` | Revoke refresh token |
| `GET` | `/auth/me` | Current user profile |

**JWT payload:**
```json
{
  "sub": "<user_id>",
  "household_id": "<household_id>",
  "role": "member",
  "exp": 1234567890
}
```

---

## Households

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/households` | Create a new household |
| `GET` | `/households/me` | Get current user's household |
| `PATCH` | `/households/me` | Update household name |
| `POST` | `/households/me/invite` | Regenerate invite code |
| `POST` | `/households/join` | Join a household by invite code |
| `GET` | `/households/me/members` | List household members |
| `DELETE` | `/households/me/members/:user_id` | Remove a member (owner only) |

---

## Recipes

### Ingestion (async pipeline)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/recipes/parse` | Submit a URL for async scrape + parse |
| `GET` | `/recipes/jobs/:job_id` | Poll scrape job status |
| `WS` | `/ws/jobs/:job_id` | WebSocket: real-time job status updates |

**POST `/recipes/parse`**
```json
// Request
{ "url": "https://www.seriouseats.com/the-best-pizza-dough-recipe" }

// Response 202
{
  "job_id": "uuid",
  "status": "pending",
  "ws_token": "short-lived-token"
}
```

**GET `/recipes/jobs/:job_id`**
```json
// Response (in-progress)
{ "job_id": "uuid", "status": "parsing", "progress": "Extracting ingredients..." }

// Response (complete)
{ "job_id": "uuid", "status": "complete", "recipe_id": "uuid" }

// Response (failed)
{ "job_id": "uuid", "status": "failed", "error": "Could not extract recipe content from URL" }
```

### Recipe CRUD

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/recipes` | List recipes (filterable) |
| `GET` | `/recipes/:id` | Get recipe detail |
| `POST` | `/recipes` | Create recipe manually |
| `PATCH` | `/recipes/:id` | Update recipe metadata |
| `DELETE` | `/recipes/:id` | Delete recipe |

**GET `/recipes`** — query params:

| Param | Type | Example |
|---|---|---|
| `q` | string | `"pasta"` — keyword search |
| `occasion` | string[] | `occasion=weeknight&occasion=holiday` |
| `complexity` | string | `easy\|medium\|hard` |
| `max_cook_time` | int | `45` (minutes) |
| `cuisine` | string | `"Italian"` |
| `diet` | string[] | `diet=vegetarian` |
| `ingredient` | string[] | `ingredient=chicken&ingredient=garlic` |
| `can_make` | bool | `true` — filter to recipes makeable from current pantry |
| `sort` | string | `relevance\|created_at\|cook_time` |
| `page` | int | `1` |
| `per_page` | int | `20` |

**GET `/recipes/:id`**
```json
{
  "id": "uuid",
  "title": "Classic Margherita Pizza",
  "description": "...",
  "source_url": "https://...",
  "image_url": "https://...",
  "servings": 4,
  "cook_time_minutes": 25,
  "prep_time_minutes": 15,
  "complexity": "medium",
  "occasions": ["weeknight", "casual"],
  "cuisine": "Italian",
  "diet_tags": ["vegetarian"],
  "ingredients": [
    {
      "id": "uuid",
      "name": "all-purpose flour",
      "quantity": 500,
      "unit": "g",
      "preparation": null,
      "optional": false,
      "raw_text": "500g all-purpose flour",
      "estimated_cost": 0.75
    }
  ],
  "steps": [
    { "step_number": 1, "instruction": "Mix flour and salt..." }
  ],
  "total_estimated_cost": 8.40,
  "created_at": "2026-01-15T10:00:00Z"
}
```

---

## Pantry

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/pantry` | Current pantry state for household |
| `POST` | `/pantry/items` | Add ingredient to pantry |
| `PATCH` | `/pantry/items/:id` | Update quantity |
| `DELETE` | `/pantry/items/:id` | Remove ingredient from pantry |
| `POST` | `/pantry/scan` | Look up ingredient by barcode |
| `POST` | `/pantry/cook` | Deduct ingredients for a cooked recipe |
| `GET` | `/pantry/events` | Pantry change history (event log) |

**POST `/pantry/items`**
```json
// Request
{
  "ingredient_id": "uuid",   // or use ingredient_name for auto-lookup/create
  "ingredient_name": "all-purpose flour",
  "quantity": 1000,
  "unit": "g"
}
```

**POST `/pantry/scan`**
```json
// Request
{ "barcode": "012345678901" }

// Response
{
  "ingredient": { "id": "uuid", "name": "all-purpose flour", "category": "dry_goods" },
  "in_pantry": { "quantity": 500, "unit": "g" }
}
```

**POST `/pantry/cook`**
```json
// Request
{ "recipe_id": "uuid", "servings": 2 }

// Response 200 — success, returns updated pantry state
{
  "deducted": [
    { "ingredient": "all-purpose flour", "quantity": 250, "unit": "g", "remaining": 750 }
  ],
  "pantry": [ /* full updated pantry_items */ ]
}

// Response 422 — insufficient ingredients
{
  "error": "insufficient_ingredients",
  "shortages": [
    { "ingredient": "mozzarella", "needed": 200, "available": 50, "unit": "g" }
  ]
}
```

**GET `/pantry`**
```json
{
  "items": [
    {
      "id": "uuid",
      "ingredient": { "id": "uuid", "name": "all-purpose flour", "category": "dry_goods" },
      "quantity": 1000,
      "unit": "g",
      "updated_at": "2026-01-15T10:00:00Z"
    }
  ]
}
```

---

## Meal Plans

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/meal-plans` | List meal plans for household |
| `POST` | `/meal-plans` | Create a new meal plan |
| `GET` | `/meal-plans/:id` | Get meal plan with entries |
| `DELETE` | `/meal-plans/:id` | Delete meal plan |
| `POST` | `/meal-plans/:id/entries` | Add recipe to meal plan |
| `PATCH` | `/meal-plans/:id/entries/:entry_id` | Update serving size or date |
| `DELETE` | `/meal-plans/:id/entries/:entry_id` | Remove entry |
| `GET` | `/meal-plans/:id/pantry-impact` | What will be consumed by this plan |
| `GET` | `/meal-plans/current` | This week's meal plan |

**GET `/meal-plans/:id/pantry-impact`**
```json
{
  "covered": [
    { "ingredient": "garlic", "needed": 6, "available": 10, "unit": "cloves" }
  ],
  "shortages": [
    { "ingredient": "heavy cream", "needed": 240, "available": 0, "unit": "ml" }
  ],
  "partially_covered": [
    { "ingredient": "parmesan", "needed": 200, "available": 80, "unit": "g", "to_buy": 120 }
  ]
}
```

---

## Shopping Lists

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/shopping-lists` | Generate list from meal plan or recipe IDs |
| `GET` | `/shopping-lists` | List shopping lists for household |
| `GET` | `/shopping-lists/:id` | Get list with items |
| `PATCH` | `/shopping-lists/:id/items/:item_id` | Check/uncheck item, update quantity |
| `GET` | `/shopping-lists/:id/export/pdf` | Download as PDF |
| `GET` | `/shopping-lists/:id/export/notes` | Get Apple Notes-compatible plain text |

**POST `/shopping-lists`**
```json
// From a meal plan
{ "meal_plan_id": "uuid" }

// From specific recipes
{ "recipe_ids": ["uuid1", "uuid2"], "servings_map": { "uuid1": 2, "uuid2": 4 } }
```

**GET `/shopping-lists/:id/export/notes`**
```
Returns plain text formatted for copy-paste into Apple Notes:

🛒 Shopping List — Week of Jan 13

PRODUCE
☐ garlic (4 cloves) — ~$0.30
☐ cherry tomatoes (400g) — ~$2.50

DAIRY
☐ parmesan (120g) — ~$3.20
☐ heavy cream (240ml) — ~$1.80

TOTAL ESTIMATED: ~$14.20
```

---

## Ingredient Costs

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/ingredients/:id/cost` | Get cost for ingredient |
| `PATCH` | `/ingredients/:id/cost` | Override cost for household |

---

## AI Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/ai/search` | Natural language recipe search |
| `POST` | `/ai/meal-plan` | Generate a meal plan via NL prompt |
| `POST` | `/ai/substitute` | Suggest ingredient substitutions |
| `POST` | `/ai/can-make` | NL query: "what can I make tonight?" |

**POST `/ai/search`**
```json
// Request
{ "query": "something quick and vegetarian with what I have in my pantry" }

// Response
{
  "results": [
    {
      "recipe": { /* recipe object */ },
      "score": 0.91,
      "match_reason": "Matches your vegetarian filter; uses garlic, tomatoes, and pasta already in your pantry"
    }
  ]
}
```

**POST `/ai/meal-plan`**
```json
// Request
{
  "prompt": "Plan 5 weeknight dinners under 45 minutes, no red meat, using as much of my pantry as possible",
  "week_start_date": "2026-01-20"
}

// Response: creates meal plan entries, returns meal plan
{ "meal_plan": { /* meal plan object */ }, "shopping_needed": true }
```

**POST `/ai/substitute`**
```json
// Request
{ "recipe_id": "uuid", "ingredient_id": "uuid" }

// Response
{
  "substitutes": [
    {
      "name": "Greek yogurt",
      "quantity": 240,
      "unit": "ml",
      "notes": "Use in equal parts. Will be slightly tangier; works well in baked goods and sauces.",
      "in_pantry": true
    }
  ]
}
```

---

## GraphQL

Available at `/graphql`. Recommended for complex queries that need nested data in a single request.

```graphql
# Get recipes I can make this week with pantry + meal plan context
query WeeklyMealContext {
  household {
    pantry {
      items { ingredient { name } quantity unit }
    }
    mealPlan(weekOf: "2026-01-20") {
      entries {
        plannedDate
        mealType
        servings
        recipe {
          title
          cookTimeMinutes
          complexity
          ingredients {
            ingredient { name }
            quantity
            unit
          }
        }
      }
      pantryImpact {
        shortages { ingredient { name } needed available unit }
      }
    }
  }
}

# Flexible recipe search with multiple filters
query SearchRecipes($filter: RecipeFilter!) {
  recipes(filter: $filter) {
    edges {
      node {
        id title cookTimeMinutes complexity
        ingredients { ingredient { name } quantity unit }
        estimatedCost
      }
    }
  }
}
```
