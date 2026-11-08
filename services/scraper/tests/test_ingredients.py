from scraper.ingredients import normalize_ingredient_name


def test_normalize_lowercases() -> None:
    assert normalize_ingredient_name("Garlic") == "garlic"


def test_normalize_singularizes_plural() -> None:
    assert normalize_ingredient_name("tomatoes") == "tomato"


def test_normalize_leaves_already_singular_unchanged() -> None:
    assert normalize_ingredient_name("flour") == "flour"


def test_normalize_strips_whitespace() -> None:
    assert normalize_ingredient_name("  spaghetti  ") == "spaghetti"


def test_normalize_none_returns_none() -> None:
    assert normalize_ingredient_name(None) is None


def test_normalize_blank_returns_none() -> None:
    assert normalize_ingredient_name("   ") is None
