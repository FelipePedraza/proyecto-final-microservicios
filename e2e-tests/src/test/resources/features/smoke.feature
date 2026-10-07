# language: es
@smoke
Característica: Escenario de humo del sistema desplegado
  Para saber que el sistema completo está en pie antes de ejecutar el resto de pruebas
  como equipo de QA
  quiero comprobar que el API Gateway responde y expone su documentación

  Antecedentes:
    Dado que el sistema está desplegado y accesible a través del gateway

  Escenario: El endpoint de salud del gateway está activo
    Cuando consulto el endpoint "/health"
    Entonces la respuesta HTTP es 200
    Y el campo "status" de la respuesta es "healthy"

  Escenario: La especificación OpenAPI del gateway está disponible sin autenticación
    Cuando consulto el endpoint "/v3/api-docs"
    Entonces la respuesta HTTP es 200
    Y la respuesta contiene el campo "openapi"
