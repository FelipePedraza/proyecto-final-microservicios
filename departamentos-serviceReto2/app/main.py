import logging
from fastapi import FastAPI, Request, status
from fastapi.encoders import jsonable_encoder
from fastapi.openapi.docs import get_redoc_html, get_swagger_ui_html
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from sqlalchemy import text
from sqlalchemy.exc import OperationalError, InterfaceError, SQLAlchemyError
from fastapi.openapi.utils import get_openapi

from app.api.departments_api import router as departamentos_router
from app.db.database import engine
from app.exceptions import DepartamentoNoEncontradoError, DepartamentoYaExisteError
from app.schemas.respuestas import (
    DatabaseUnavailableResponse,
    HealthResponse,
    ReadyResponse,
)

logger = logging.getLogger(__name__)

app = FastAPI(
    title="Departamentos Service",
    description=(
        "API para crear, consultar y listar departamentos. "
        "Las operaciones de departamentos deben enviarse mediante el gateway "
        "con un JWT Bearer; la autenticación y autorización son responsabilidad "
        "del gateway, no de este servicio."
    ),
    version="1.0.0",
    openapi_tags=[
        {"name": "Departamentos", "description": "Operaciones de departamentos."},
        {"name": "Health", "description": "Liveness y disponibilidad de la base de datos."},
    ],
)


def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema

    schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
        tags=app.openapi_tags,
    )
    schema.setdefault("components", {}).setdefault("securitySchemes", {})[
        "BearerAuth"
    ] = {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
        "description": "JWT validado por el gateway.",
    }
    for path, path_item in schema["paths"].items():
        if path in (
            "/departamentos",
            "/departamentos/{id}",
            "/health/ready",
        ):
            for operation in path_item.values():
                if isinstance(operation, dict) and "responses" in operation:
                    operation["security"] = [{"BearerAuth": []}]

    app.openapi_schema = schema
    return app.openapi_schema


app.openapi = custom_openapi


@app.get("/departamentos/openapi.json", include_in_schema=False)
def prefixed_openapi():
    """Alias para servir OpenAPI mediante el prefijo /departamentos del gateway."""
    return app.openapi()


@app.get("/departamentos/docs", include_in_schema=False)
def prefixed_swagger_ui():
    """Swagger UI accesible a través de la ruta del servicio en el gateway."""
    return get_swagger_ui_html(
        openapi_url="/departamentos/openapi.json",
        title=f"{app.title} - Swagger UI",
    )


@app.get("/departamentos/redoc", include_in_schema=False)
def prefixed_redoc():
    """ReDoc accesible a través de la ruta del servicio en el gateway."""
    return get_redoc_html(
        openapi_url="/departamentos/openapi.json",
        title=f"{app.title} - ReDoc",
    )


app.include_router(departamentos_router)


def _error(status_code: int, mensaje: str, headers: dict | None = None) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"error": mensaje}, headers=headers)


# ---------- 503: la base de datos no está disponible ----------
@app.exception_handler(OperationalError)
@app.exception_handler(InterfaceError)
async def db_no_disponible_handler(request: Request, exc: Exception):
    logger.error("BD de departamentos no disponible en %s: %s", request.url.path, exc)
    return _error(
        status.HTTP_503_SERVICE_UNAVAILABLE,
        "El servicio de departamentos no está disponible: no hay conexión con su base de datos.",
        headers={"Retry-After": "5"},
    )


# ---------- 500: cualquier otro fallo de persistencia (SQL inválido, esquema, etc.) ----------
@app.exception_handler(SQLAlchemyError)
async def db_error_handler(request: Request, exc: SQLAlchemyError):
    logger.exception("Error de persistencia en %s", request.url.path)
    return _error(status.HTTP_500_INTERNAL_SERVER_ERROR, "Error interno al acceder a los datos.")


# ---------- Errores de negocio ----------
@app.exception_handler(DepartamentoNoEncontradoError)
async def no_encontrado_handler(request: Request, exc: DepartamentoNoEncontradoError):
    return _error(status.HTTP_404_NOT_FOUND, str(exc))


@app.exception_handler(DepartamentoYaExisteError)
async def ya_existe_handler(request: Request, exc: DepartamentoYaExisteError):
    return _error(status.HTTP_409_CONFLICT, str(exc))


# ---------- 422 con el mismo formato que el resto ----------
@app.exception_handler(RequestValidationError)
async def validacion_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "error": "Los datos enviados no son válidos.",
            "detalles": jsonable_encoder(exc.errors()),
        },
    )


# ---------- Rutas y métodos no soportados, con formato uniforme ----------
@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request: Request, exc: StarletteHTTPException):
    if exc.status_code == status.HTTP_404_NOT_FOUND:
        return _error(404, "Recurso no encontrado")
    if exc.status_code == status.HTTP_405_METHOD_NOT_ALLOWED:
        return _error(405, f"El método {request.method} no está permitido en {request.url.path}")
    detalle = exc.detail if isinstance(exc.detail, str) else "Error en la solicitud"
    return _error(exc.status_code, detalle)


# ---------- Red de seguridad: nada sale con el stacktrace ----------
@app.exception_handler(Exception)
async def unhandled_handler(request: Request, exc: Exception):
    logger.exception("Excepción no controlada en %s", request.url.path)
    return _error(status.HTTP_500_INTERNAL_SERVER_ERROR, "Ocurrió un error interno del servidor.")


# ---------- Health: liveness vs readiness ----------
@app.get(
    "/health",
    tags=["Health"],
    response_model=HealthResponse,
    response_description="El proceso está en ejecución.",
)
def health():
    """Liveness: el proceso está vivo."""
    return {"status": "healthy"}


@app.get(
    "/health/ready",
    tags=["Health"],
    response_model=ReadyResponse,
    response_description="La base de datos está disponible.",
    responses={
        503: {
            "model": DatabaseUnavailableResponse,
            "description": "La base de datos no está disponible.",
            "headers": {
                "Retry-After": {
                    "description": "Segundos sugeridos antes de reintentar.",
                    "schema": {"type": "string", "example": "5"},
                }
            },
        }
    },
)
def ready():
    """Readiness: el servicio puede atender tráfico (incluye su base de datos)."""
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return {"status": "ready", "database": "up"}
    except SQLAlchemyError:
        logger.warning("Readiness fallida: base de datos inaccesible")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"status": "not_ready", "database": "down"},
            headers={"Retry-After": "5"},
        )