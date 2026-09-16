"""Casos de prueba para el microservicio de departamentos.

Estas pruebas validan el comportamiento principal de la API REST del servicio,
utilizando FastAPI TestClient y una base SQLite en memoria.
"""


def test_health_endpoint(client):
    """El endpoint de salud debe responder con estado healthy."""
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "healthy"}


def test_create_and_get_department(client):
    """Debe permitir crear un departamento y luego consultarlo por ID."""
    payload = {"id": "IT", "name": "Tecnología", "description": "Departamento de TI"}

    create_response = client.post("/departamentos", json=payload)

    assert create_response.status_code == 201
    assert create_response.json()["id"] == "IT"
    assert create_response.json()["name"] == "Tecnología"

    get_response = client.get("/departamentos/IT")

    assert get_response.status_code == 200
    assert get_response.json() == payload


def test_list_departments_returns_created_items(client):
    """Un departamento creado debe aparecer en la lista general."""
    payload = {"id": "FIN", "name": "Finanzas", "description": "Departamento financiero"}

    client.post("/departamentos", json=payload)
    response = client.get("/departamentos")

    assert response.status_code == 200
    assert any(item["id"] == "FIN" for item in response.json())


def test_get_missing_department_returns_404(client):
    """Si el departamento no existe, debe devolver 404 con el mensaje esperado."""
    response = client.get("/departamentos/NO-EXISTE")

    assert response.status_code == 404
    assert response.json() == {"error": "El departamento con id NO-EXISTE no existe"}
