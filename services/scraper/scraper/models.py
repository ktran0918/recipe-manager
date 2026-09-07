from uuid import UUID

from pydantic import BaseModel


# Mirrors the Java ScrapeJobMessage record published by RabbitMqConfig.
# Field names are snake_case to match the wire format enforced by the API's
# spring.jackson.property-naming-strategy (see docs/stories.md contract section).
class ScrapeJobMessage(BaseModel):
    job_id: UUID
    url: str
    household_id: UUID
