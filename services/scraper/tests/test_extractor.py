from pathlib import Path

import pytest

from scraper.errors import FetchError
from scraper.extractor import extract_article_text

FIXTURES_DIR = Path(__file__).parent / "fixtures"


def test_extract_article_text_returns_clean_text():
    html = (FIXTURES_DIR / "sample_recipe.html").read_text()

    text = extract_article_text(html)

    assert "garlic butter pasta" in text.lower()
    assert "8 oz spaghetti" in text
    # Boilerplate the fixture deliberately includes should be filtered out.
    assert "Advertise with us" not in text
    assert "Sponsored content" not in text


def test_extract_article_text_too_short_raises_fetch_error():
    html = "<html><body><p>Hi</p></body></html>"

    with pytest.raises(FetchError):
        extract_article_text(html)


def test_extract_article_text_empty_raises_fetch_error():
    html = "<html><body></body></html>"

    with pytest.raises(FetchError):
        extract_article_text(html)
