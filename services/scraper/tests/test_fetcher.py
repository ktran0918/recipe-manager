import httpx
import pytest

from scraper.errors import FetchError
from scraper.fetcher import MIN_STATIC_CONTENT_LENGTH, fetch_html


async def test_fetch_html_returns_static_content_when_long_enough(patch_httpx_transport):
    body = "<html><body>" + ("x" * MIN_STATIC_CONTENT_LENGTH) + "</body></html>"
    patch_httpx_transport(lambda request: httpx.Response(200, text=body))

    html = await fetch_html("https://example.com/recipe")

    assert html == body


async def test_fetch_html_raises_fetch_error_on_http_error(patch_httpx_transport):
    patch_httpx_transport(lambda request: httpx.Response(404, text="not found"))

    with pytest.raises(FetchError):
        await fetch_html("https://example.com/missing")


async def test_fetch_html_falls_back_to_rendered_when_content_too_short(patch_httpx_transport, monkeypatch):
    patch_httpx_transport(lambda request: httpx.Response(200, text="<html><body>short</body></html>"))

    called_with = {}

    async def fake_rendered(url: str) -> str:
        called_with["url"] = url
        return "<html><body>rendered content</body></html>"

    monkeypatch.setattr("scraper.fetcher._fetch_rendered", fake_rendered)

    html = await fetch_html("https://example.com/spa-recipe")

    assert html == "<html><body>rendered content</body></html>"
    assert called_with["url"] == "https://example.com/spa-recipe"


async def test_fetch_html_falls_back_when_js_gate_marker_present(patch_httpx_transport, monkeypatch):
    body = "please enable javascript to view this page. " + ("x" * MIN_STATIC_CONTENT_LENGTH)
    patch_httpx_transport(lambda request: httpx.Response(200, text=body))

    async def fake_rendered(url: str) -> str:
        return "rendered"

    monkeypatch.setattr("scraper.fetcher._fetch_rendered", fake_rendered)

    html = await fetch_html("https://example.com/js-gated")

    assert html == "rendered"
