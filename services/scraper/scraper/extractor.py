import trafilatura

from scraper.errors import FetchError

# Below this, trafilatura most likely grabbed nav/ad boilerplate rather than a real recipe —
# not worth sending to the LLM parser in EP2-04.
MIN_ARTICLE_LENGTH = 200


def extract_article_text(html: str) -> str:
    # trafilatura ships no type stubs, so its return is Any to mypy — the explicit annotation
    # here is what lets the guard below narrow it to str for the return type.
    text: str | None = trafilatura.extract(html)
    if not text or len(text) < MIN_ARTICLE_LENGTH:
        raise FetchError("Extracted article content is too short or empty")
    return text
