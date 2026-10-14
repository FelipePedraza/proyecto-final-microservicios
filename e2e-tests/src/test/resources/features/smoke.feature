# language: es
@smoke
Característica: Que el sistema esté vivo
  Antes de correr todo lo demás, revisamos que el gateway responda
  y que su documentación se pueda abrir

  Antecedentes:
    Dado que el sistema está arriba y responde por el gateway

  Escenario: El gateway dice que está sano
    Cuando llamo a "/health"
    Entonces el status es 200
    Y el campo "status" vale "healthy"

  Escenario: La documentación OpenAPI se abre sin token
    Cuando llamo a "/v3/api-docs"
    Entonces el status es 200
    Y viene el campo "openapi"
