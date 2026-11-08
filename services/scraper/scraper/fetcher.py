import httpx

from scraper.config import settings
from scraper.errors import FetchError

# Below this, a "successful" static fetch is more likely an empty shell the client is meant
# to hydrate with JS (common for SPA-built recipe sites) than a real short page.
MIN_STATIC_CONTENT_LENGTH = 500

JS_GATE_MARKERS = (
    "enable javascript",
    "requires javascript",
    "please turn on javascript",
)


async def fetch_html(url: str) -> str:
    html = await _fetch_static(url)
    if _looks_js_gated(html):
        html = await _fetch_rendered(url)
    return html


async def _fetch_static(url: str) -> str:
    try:
        async with httpx.AsyncClient(timeout=settings.scrape_timeout_seconds, follow_redirects=True) as client:
            response = await client.get(url, headers={"User-Agent": "recipe-manager-scraper/0.1"})
            response.raise_for_status()
            return response.text
    except httpx.HTTPError as exc:
        raise FetchError(f"Static fetch failed for {url}: {exc}") from exc


def _looks_js_gated(html: str) -> bool:
    if len(html) < MIN_STATIC_CONTENT_LENGTH:
        return True
    lowered = html.lower()
    return any(marker in lowered for marker in JS_GATE_MARKERS)


async def _fetch_rendered(url: str) -> str:
    # Imported lazily — Playwright + its browser binary are only needed on the JS-gated
    # fallback path, so most fetches never pay the cost of loading this module.
    from playwright.async_api import async_playwright

    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch()
            try:
                page = await browser.new_page()
                await page.goto(url, timeout=settings.scrape_timeout_seconds * 1000)
                return await page.content()
            finally:
                await browser.close()
    except Exception as exc:
        raise FetchError(f"Rendered fetch failed for {url}: {exc}") from exc
