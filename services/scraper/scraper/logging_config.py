import json
import logging
import sys
from contextvars import ContextVar
from typing import Any

# ContextVar (not a plain module global) so job_id stays correct per-task under asyncio —
# concurrent consumers processing different messages must not see each other's job_id.
_job_id_var: ContextVar[str | None] = ContextVar("job_id", default=None)


def set_job_id(job_id: str | None) -> None:
    _job_id_var.set(job_id)


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "job_id": _job_id_var.get(),
        }
        if record.exc_info:
            payload["exc_info"] = self.formatException(record.exc_info)
        return json.dumps(payload)


def configure_logging(level: int = logging.INFO) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)
