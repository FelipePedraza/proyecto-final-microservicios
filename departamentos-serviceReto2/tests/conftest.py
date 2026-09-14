"""Fixtures compartidos para la suite de pruebas del microservicio de departamentos.

Este archivo prepara una base de datos SQLite en memoria para cada prueba,
permitiendo ejecutar la API con FastAPI TestClient sin depender de PostgreSQL
ni de Docker.
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.db.database import Base, get_db
from app.main import app


# SQLite en memoria compartido para que todas las conexiones de la misma prueba
# apunten a la misma base de datos y puedan ver las tablas creadas.
SQLALCHEMY_DATABASE_URL = "sqlite://"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture()
def db_session():
    """Crea la base de datos para una prueba y luego la limpia al final."""
    Base.metadata.create_all(bind=engine)
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)


@pytest.fixture()
def client(db_session):
    """Proporciona un cliente HTTP de prueba con la base de datos inyectada."""

    def override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
