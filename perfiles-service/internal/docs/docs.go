package docs

import "github.com/swaggo/swag"

var SwaggerInfo = &swag.Spec{
	Version:          "1.0",
	Host:             "",
	BasePath:         "/",
	Schemes:          []string{"http"},
	Title:            "Perfiles Service API",
	Description:      "API HTTP del servicio de perfiles. El servicio no valida JWT por sí mismo; al acceder mediante el gateway, las rutas de perfiles requieren Bearer JWT. GET requiere rol USER o ADMIN. PUT requiere autorización de propiedad del perfil.",
	InfoInstanceName: "swagger",
	SwaggerTemplate: `{
  "swagger": "2.0",
  "info": {
    "title": "Perfiles Service API",
    "version": "1.0",
    "description": "API HTTP del servicio de perfiles. El servicio no valida JWT por sí mismo; al acceder mediante el gateway, las rutas de perfiles requieren Bearer JWT. GET requiere rol USER o ADMIN. PUT requiere autorización de propiedad del perfil."
  },
  "basePath": "/",
  "schemes": ["http"],
  "securityDefinitions": {
    "BearerAuth": {
      "type": "apiKey",
      "name": "Authorization",
      "in": "header",
      "description": "JWT validado por el gateway. Envíe el valor como: Bearer <token>."
    }
  },
  "paths": {
    "/perfiles": {
      "get": {
        "tags": ["Perfiles"],
        "summary": "Lista todos los perfiles",
        "description": "Por el gateway requiere un JWT Bearer válido con rol USER o ADMIN. El servicio interno no valida tokens.",
        "security": [{"BearerAuth": []}],
        "produces": ["application/json"],
        "responses": {
          "200": {
            "description": "Perfiles ordenados por identificador de empleado.",
            "schema": {"type": "array", "items": {"$ref": "#/definitions/Perfil"}}
          },
          "401": {"description": "Falta un JWT válido al acceder por el gateway.", "schema": {"$ref": "#/definitions/GatewayError"}},
          "403": {"description": "El usuario no tiene rol USER o ADMIN.", "schema": {"$ref": "#/definitions/GatewayError"}},
          "500": {"description": "No se pudieron consultar los perfiles.", "schema": {"$ref": "#/definitions/Error"}}
        }
      }
    },
    "/perfiles/{empleadoId}": {
      "get": {
        "tags": ["Perfiles"],
        "summary": "Obtiene el perfil de un empleado",
        "description": "Por el gateway requiere un JWT Bearer válido con rol USER o ADMIN. El servicio interno no valida tokens.",
        "security": [{"BearerAuth": []}],
        "produces": ["application/json"],
        "parameters": [{
          "name": "empleadoId",
          "in": "path",
          "required": true,
          "type": "string",
          "description": "Identificador del empleado.",
          "example": "E001"
        }],
        "responses": {
          "200": {"description": "Perfil encontrado.", "schema": {"$ref": "#/definitions/Perfil"}},
          "401": {"description": "Falta un JWT válido al acceder por el gateway.", "schema": {"$ref": "#/definitions/GatewayError"}},
          "403": {"description": "El usuario no tiene rol USER o ADMIN.", "schema": {"$ref": "#/definitions/GatewayError"}},
          "404": {"description": "No existe un perfil para el empleado solicitado.", "schema": {"$ref": "#/definitions/Error"}},
          "500": {"description": "No se pudo consultar el perfil.", "schema": {"$ref": "#/definitions/Error"}}
        }
      },
      "put": {
        "tags": ["Perfiles"],
        "summary": "Actualiza los datos editables de un perfil",
        "description": "Por el gateway requiere un JWT Bearer válido; se autoriza a ADMIN o al USER dueño del empleadoId de la ruta. Envíe los cuatro campos para conservar sus valores; cualquier campo omitido se decodifica como cadena vacía y reemplaza el valor existente. No se aceptan propiedades desconocidas.",
        "security": [{"BearerAuth": []}],
        "consumes": ["application/json"],
        "produces": ["application/json"],
        "parameters": [
          {
            "name": "empleadoId",
            "in": "path",
            "required": true,
            "type": "string",
            "description": "Identificador del empleado cuyo perfil se actualiza.",
            "example": "E001"
          },
          {
            "name": "body",
            "in": "body",
            "required": true,
            "description": "Los campos omitidos toman el valor vacío; incluya los cuatro para conservarlos.",
            "schema": {"$ref": "#/definitions/UpdatePerfil"}
          }
        ],
        "responses": {
          "200": {"description": "Perfil actualizado.", "schema": {"$ref": "#/definitions/Perfil"}},
          "400": {"description": "JSON malformado, tipos incompatibles o propiedades desconocidas.", "schema": {"$ref": "#/definitions/Error"}},
          "401": {"description": "Falta un JWT válido al acceder por el gateway.", "schema": {"$ref": "#/definitions/GatewayError"}},
          "403": {"description": "El usuario no es ADMIN ni dueño del perfil solicitado.", "schema": {"$ref": "#/definitions/GatewayError"}},
          "404": {"description": "No existe un perfil para el empleado solicitado.", "schema": {"$ref": "#/definitions/Error"}},
          "500": {"description": "No se pudo actualizar el perfil.", "schema": {"$ref": "#/definitions/Error"}}
        }
      }
    },
    "/health/live": {
      "get": {
        "tags": ["Salud"],
        "summary": "Comprueba que el proceso está vivo",
        "produces": ["application/json"],
        "responses": {
          "200": {"description": "Proceso activo.", "schema": {"$ref": "#/definitions/HealthLive"}}
        }
      }
    },
    "/health/ready": {
      "get": {
        "tags": ["Salud"],
        "summary": "Comprueba la disponibilidad de la base de datos",
        "produces": ["application/json"],
        "responses": {
          "200": {"description": "Base de datos disponible.", "schema": {"$ref": "#/definitions/HealthReady"}},
          "503": {"description": "Base de datos no disponible.", "schema": {"$ref": "#/definitions/Error"}}
        }
      }
    },
    "/swagger/index.html": {
      "get": {
        "tags": ["Documentación"],
        "summary": "Swagger UI",
        "produces": ["text/html"],
        "responses": {"200": {"description": "Interfaz interactiva de Swagger."}}
      }
    },
    "/swagger/doc.json": {
      "get": {
        "tags": ["Documentación"],
        "summary": "Especificación Swagger 2.0 en JSON",
        "produces": ["application/json"],
        "responses": {"200": {"description": "Contrato OpenAPI/Swagger del servicio."}}
      }
    }
  },
  "definitions": {
    "Perfil": {
      "type": "object",
      "required": ["id", "empleadoId", "nombre", "email", "telefono", "direccion", "ciudad", "biografia", "archivado", "fechaCreacion"],
      "properties": {
        "id": {"type": "string", "format": "uuid", "example": "c5c5aa15-30f8-49e9-b90d-650b7f70d7c1"},
        "empleadoId": {"type": "string", "example": "E001"},
        "nombre": {"type": "string", "example": "Juan Pérez"},
        "email": {"type": "string", "format": "email", "example": "juan.perez@empresa.com"},
        "telefono": {"type": "string", "example": "+525512345678"},
        "direccion": {"type": "string", "example": "Av. Reforma 100"},
        "ciudad": {"type": "string", "example": "Ciudad de México"},
        "biografia": {"type": "string", "example": "Ingeniero de software"},
        "archivado": {"type": "boolean", "example": false},
        "fechaCreacion": {"type": "string", "format": "date-time", "example": "2026-02-10T10:00:00Z"},
        "fechaArchivado": {"type": "string", "format": "date-time", "example": "2026-03-01T10:00:00Z"}
      }
    },
    "UpdatePerfil": {
      "type": "object",
      "additionalProperties": false,
      "required": ["telefono", "direccion", "ciudad", "biografia"],
      "properties": {
        "telefono": {"type": "string", "example": "+525512345678"},
        "direccion": {"type": "string", "example": "Av. Reforma 100"},
        "ciudad": {"type": "string", "example": "Ciudad de México"},
        "biografia": {"type": "string", "example": "Ingeniero de software"}
      }
    },
    "Error": {
      "type": "object",
      "required": ["error"],
      "properties": {"error": {"type": "string", "example": "body inválido"}}
    },
    "GatewayError": {
      "type": "object",
      "required": ["error", "message"],
      "properties": {
        "error": {"type": "string", "enum": ["unauthorized", "forbidden"]},
        "message": {"type": "string"}
      }
    },
    "HealthLive": {
      "type": "object",
      "required": ["status"],
      "properties": {"status": {"type": "string", "enum": ["healthy"], "example": "healthy"}}
    },
    "HealthReady": {
      "type": "object",
      "required": ["status", "database"],
      "properties": {
        "status": {"type": "string", "enum": ["ready"], "example": "ready"},
        "database": {"type": "string", "enum": ["up"], "example": "up"}
      }
    }
  }
}`,
}

func init() { swag.Register(SwaggerInfo.InstanceName(), SwaggerInfo) }
