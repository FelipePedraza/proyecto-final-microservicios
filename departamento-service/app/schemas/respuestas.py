from typing import Any, Literal

from pydantic import BaseModel


class ErrorResponse(BaseModel):
    error: str


class ValidationErrorResponse(ErrorResponse):
    detalles: list[dict[str, Any]]


class HealthResponse(BaseModel):
    status: Literal["UP", "DOWN"]
    service: Literal["departamento-service"]
    checks: dict[str, Literal["UP", "DOWN"]]


class ReadyResponse(BaseModel):
    status: Literal["ready"]
    database: Literal["up"]


class DatabaseUnavailableResponse(BaseModel):
    status: Literal["not_ready"]
    database: Literal["down"]
