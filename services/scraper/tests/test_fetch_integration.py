from pathlib import Path

import httpx

from scraper.extractor import extract_article_text
from scraper.fetcher import fetch_html

FIXTURES_DIR = Path(__file__).parent / "fixtures"


# "Real URL" here means the fetch_html + extract_article_text pipeline runs end to end against
# a real saved HTML page (nav, ads, and all) rather than live network — matching EP2-03's AC to
# test with a known-good fixture file, not a live request that could break the suite offline.
async def test_fetch_and_extract_real_recipe_fixture(patch_httpx_transport):
    fixture_html = (FIXTURES_DIR / "sample_recipe.html").read_text()
    patch_httpx_transport(lambda request: httpx.Response(200, text=fixture_html))

    html = await fetch_html("https://example.com/garlic-butter-pasta")
    text = extract_article_text(html)

    assert "garlic butter pasta" in text.lower()
    assert "spaghetti" in text.lower()
    assert "Advertise with us" not in text
