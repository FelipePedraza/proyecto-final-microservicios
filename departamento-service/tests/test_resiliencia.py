from unittest.mock import patch
from sqlalchemy.exc import OperationalError


def test_get_department_con_bd_caida_devuelve_503(client):
    with patch("app.repositories.department_repository.department_repository.get_by_id",
               side_effect=OperationalError("SELECT 1", {}, Exception("connection refused"))):
        response = client.get("/departamentos/IT")

    assert response.status_code == 503
    assert response.headers["Retry-After"] == "5"
    assert "error" in response.json()


def test_create_department_duplicado_devuelve_409(client):
    payload = {"id": "IT", "name": "Tecnología", "description": "TI"}
    assert client.post("/departamentos", json=payload).status_code == 201
    assert client.post("/departamentos", json=payload).status_code == 409


def test_departamento_con_description_nula_no_rompe(client, db_session):
    from app.model.department_model import DepartmentModel
    db_session.add(DepartmentModel(id="OPS", name="Operaciones", description=None))
    db_session.commit()
    assert client.get("/departamentos/OPS").status_code == 200