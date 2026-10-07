
# DEFINIMOS QUE VA A SER EL CONTROLADOR (ROUTER )
from app.schemas.departamento import Department
from app.service.department_service import Department_service
from fastapi import APIRouter, Depends, status
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
from app.db.database import get_db
from app.schemas.respuestas import (
    ErrorResponse,
    ValidationErrorResponse,
)


router = APIRouter(prefix="/departamentos", tags=["Departamentos"])


@router.post(
    "",
    response_model=Department,
    status_code=status.HTTP_201_CREATED,
    response_description="Departamento creado.",
    summary="Crear un departamento",
    description="Registra un nuevo departamento. El identificador debe ser único.",
    responses={
        409: {
            "model": ErrorResponse,
            "description": "Ya existe un departamento con ese identificador.",
        },
        422: {
            "model": ValidationErrorResponse,
            "description": "El cuerpo de la solicitud no cumple el esquema.",
        },
        500: {
            "model": ErrorResponse,
            "description": "Error interno al acceder a los datos o fallo no controlado.",
        },
        503: {
            "model": ErrorResponse,
            "description": "No hay conexión con la base de datos.",
            "headers": {
                "Retry-After": {
                    "description": "Segundos sugeridos antes de reintentar.",
                    "schema": {"type": "string", "example": "5"},
                }
            },
        },
    },
)
def create_department(department: Department, db: Session = Depends(get_db)):
    return Department_service(db).create_department(department)

@router.get(
    "/{id}",
    response_model=Department,
    status_code=status.HTTP_200_OK,
    response_description="Departamento encontrado.",
    summary="Consultar un departamento",
    description="Busca un departamento por su identificador.",
    responses={
        404: {
            "model": ErrorResponse,
            "description": "No existe un departamento con ese identificador.",
        },
        500: {
            "model": ErrorResponse,
            "description": "Error interno al acceder a los datos o fallo no controlado.",
        },
        503: {
            "model": ErrorResponse,
            "description": "No hay conexión con la base de datos.",
            "headers": {
                "Retry-After": {
                    "description": "Segundos sugeridos antes de reintentar.",
                    "schema": {"type": "string", "example": "5"},
                }
            },
        },
    },
)
def get_department(id: str, db: Session = Depends(get_db)):
    return Department_service(db).get_department(id)

@router.get(
    "",
    response_model=list[Department],
    status_code=status.HTTP_200_OK,
    response_description="Lista de departamentos.",
    summary="Listar departamentos",
    description="Devuelve todos los departamentos registrados.",
    responses={
        500: {
            "model": ErrorResponse,
            "description": "Error interno al acceder a los datos o fallo no controlado.",
        },
        503: {
            "model": ErrorResponse,
            "description": "No hay conexión con la base de datos.",
            "headers": {
                "Retry-After": {
                    "description": "Segundos sugeridos antes de reintentar.",
                    "schema": {"type": "string", "example": "5"},
                }
            },
        },
    },
)
def list_departments(db: Session = Depends(get_db)):
    return Department_service(db).list_departments()