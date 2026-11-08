from collections.abc import Callable

import httpx
import pytest

_RealAsyncClient = httpx.AsyncClient


HttpxHandler = Callable[[httpx.Request], httpx.Response]


@pytest.fixture
def patch_httpx_transport(monkeypatch: pytest.MonkeyPatch) -> Callable[[HttpxHandler], None]:
    # httpx.MockTransport is httpx's own testing seam — swapping it in here means fetcher.py
    # never needs a test-only constructor param, and every test that hits the network goes
    # through the exact same request/response path production code does.
    def _patch(handler: HttpxHandler) -> None:
        def _factory(*args: object, **kwargs: object) -> httpx.AsyncClient:
            kwargs["transport"] = httpx.MockTransport(handler)
            return _RealAsyncClient(*args, **kwargs)

        monkeypatch.setattr("scraper.fetcher.httpx.AsyncClient", _factory)

    return _patch
