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


def test_invalid_body_returns_422_instead_of_500(client):
    """Un cuerpo mal formado debe devolver un error de validación serializable."""
    response = client.post(
        "/departamentos",
        content="'{id:",
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 422
    assert response.json()["error"] == "Los datos enviados no son válidos."


def test_openapi_documents_endpoints_auth_and_responses(client):
    response = client.get("/openapi.json")

    assert response.status_code == 200
    schema = response.json()
    assert set(schema["paths"]) == {
        "/departamentos",
        "/departamentos/{id}",
        "/health",
        "/health/ready",
    }
    assert set(schema["paths"]["/departamentos"]) == {"get", "post"}
    assert set(schema["paths"]["/departamentos/{id}"]) == {"get"}
    assert schema["paths"]["/departamentos"]["post"]["security"] == [
        {"BearerAuth": []}
    ]
    assert schema["paths"]["/health/ready"]["get"]["security"] == [
        {"BearerAuth": []}
    ]
    assert schema["paths"]["/health"]["get"].get("security") is None
    assert schema["components"]["securitySchemes"]["BearerAuth"] == {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
        "description": "JWT validado por el gateway.",
    }

    create_responses = schema["paths"]["/departamentos"]["post"]["responses"]
    assert {"201", "409", "422", "500", "503"} <= set(create_responses)
    assert {"200", "404", "500", "503"} <= set(
        schema["paths"]["/departamentos/{id}"]["get"]["responses"]
    )
    assert {"200", "503"} <= set(schema["paths"]["/health/ready"]["get"]["responses"])


def test_prefixed_docs_aliases_keep_service_root_docs(client):
    root_docs = client.get("/docs")
    prefixed_docs = client.get("/departamentos/docs")
    root_openapi = client.get("/openapi.json")
    prefixed_openapi = client.get("/departamentos/openapi.json")
    prefixed_redoc = client.get("/departamentos/redoc")

    assert root_docs.status_code == 200
    assert prefixed_docs.status_code == 200
    assert "/departamentos/openapi.json" in prefixed_docs.text
    assert root_openapi.status_code == prefixed_openapi.status_code == 200
    assert root_openapi.json() == prefixed_openapi.json()
    assert prefixed_redoc.status_code == 200
    assert "/departamentos/openapi.json" in prefixed_redoc.text
