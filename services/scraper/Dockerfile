FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml ./
COPY scraper ./scraper

RUN pip install --no-cache-dir .
# The pip package alone only gets the driver — the browser binary and its OS-level deps
# (fonts, codecs, etc.) are a separate install, otherwise the JS-render fallback in
# fetcher.py crashes the first time it actually needs to launch Chromium.
RUN playwright install --with-deps chromium

CMD ["python", "-m", "scraper.main"]
