package docs

import "github.com/swaggo/swag"

var SwaggerInfo = &swag.Spec{
	Version: "1.0", Host: "", BasePath: "/", Schemes: []string{"http"},
	Title: "Perfiles Service API", Description: "API de perfiles de empleados",
	InfoInstanceName: "swagger", SwaggerTemplate: `{"swagger":"2.0","info":{"title":"Perfiles Service API","version":"1.0"},"paths":{"/perfiles":{"get":{"responses":{"200":{"description":"Lista de perfiles"}}}},"/perfiles/{empleadoId}":{"get":{"responses":{"200":{"description":"Perfil"},"404":{"description":"No encontrado"}}},"put":{"responses":{"200":{"description":"Perfil actualizado"},"400":{"description":"Body inválido"},"404":{"description":"No encontrado"}}}}}}`,
}
func init(){swag.Register(SwaggerInfo.InstanceName(),SwaggerInfo)}
