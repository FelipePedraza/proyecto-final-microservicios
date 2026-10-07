package api

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/domain"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/repository"
)

type fakeRepository struct{ profile domain.Perfil }

func (f *fakeRepository) List(context.Context) ([]domain.Perfil, error) {
	return []domain.Perfil{f.profile}, nil
}
func (f *fakeRepository) Get(_ context.Context, id string) (domain.Perfil, error) {
	if id != f.profile.EmpleadoID {
		return domain.Perfil{}, repository.ErrNotFound
	}
	return f.profile, nil
}
func (f *fakeRepository) Update(_ context.Context, id string, u domain.UpdatePerfil) (domain.Perfil, error) {
	if id != f.profile.EmpleadoID {
		return domain.Perfil{}, repository.ErrNotFound
	}
	f.profile.Telefono = u.Telefono
	f.profile.Direccion = u.Direccion
	f.profile.Ciudad = u.Ciudad
	f.profile.Biografia = u.Biografia
	return f.profile, nil
}
func (*fakeRepository) Ready(context.Context) error { return nil }

func TestGetProfileReturns404(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodGet, "/perfiles/E999", nil)
	res := httptest.NewRecorder()
	r.ServeHTTP(res, req)
	if res.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", res.Code)
	}
}

func TestPutProfileRejectsInvalidJSON(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodPut, "/perfiles/E001", strings.NewReader(`{"telefono":42}`))
	res := httptest.NewRecorder()
	r.ServeHTTP(res, req)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", res.Code)
	}
	var body map[string]string
	if err := json.Unmarshal(res.Body.Bytes(), &body); err != nil || body["error"] == "" {
		t.Fatalf("unexpected error body: %s", res.Body.String())
	}
}

func TestPutProfileRejectsMissingFields(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodPut, "/perfiles/E001", strings.NewReader(
		`{"telefono":"+525512345678","direccion":"Av. Reforma 100","ciudad":"Ciudad de México"}`,
	))
	res := httptest.NewRecorder()
	r.ServeHTTP(res, req)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", res.Code)
	}
}

func TestSwaggerJSONDocumentsRoutesAndGatewayBearerAuth(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodGet, "/swagger/doc.json", nil)
	res := httptest.NewRecorder()
	r.ServeHTTP(res, req)

	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var spec struct {
		Swagger             string                                `json:"swagger"`
		Paths               map[string]map[string]json.RawMessage `json:"paths"`
		SecurityDefinitions map[string]json.RawMessage            `json:"securityDefinitions"`
		Definitions         map[string]json.RawMessage            `json:"definitions"`
	}
	if err := json.Unmarshal(res.Body.Bytes(), &spec); err != nil {
		t.Fatalf("invalid Swagger JSON: %v", err)
	}
	if spec.Swagger != "2.0" {
		t.Fatalf("expected Swagger 2.0, got %q", spec.Swagger)
	}
	for _, path := range []string{"/perfiles", "/perfiles/{empleadoId}", "/health/live", "/health/ready"} {
		if _, ok := spec.Paths[path]; !ok {
			t.Errorf("spec is missing route %q", path)
		}
	}
	if _, ok := spec.SecurityDefinitions["BearerAuth"]; !ok {
		t.Error("spec is missing the gateway Bearer JWT security definition")
	}
	var bearerScheme struct {
		Type string `json:"type"`
		Name string `json:"name"`
		In   string `json:"in"`
	}
	if err := json.Unmarshal(spec.SecurityDefinitions["BearerAuth"], &bearerScheme); err != nil {
		t.Fatalf("invalid bearer security definition: %v", err)
	}
	if bearerScheme.Type != "apiKey" || bearerScheme.Name != "Authorization" || bearerScheme.In != "header" {
		t.Errorf("BearerAuth must use the Authorization header, got %#v", bearerScheme)
	}
	for _, route := range []struct {
		path   string
		method string
	}{
		{path: "/perfiles", method: http.MethodGet},
		{path: "/perfiles/{empleadoId}", method: http.MethodGet},
		{path: "/perfiles/{empleadoId}", method: http.MethodPut},
	} {
		var operation struct {
			Security []map[string][]string `json:"security"`
		}
		if err := json.Unmarshal(spec.Paths[route.path][strings.ToLower(route.method)], &operation); err != nil {
			t.Fatalf("invalid %s operation for %s: %v", route.method, route.path, err)
		}
		if len(operation.Security) == 0 {
			t.Errorf("%s %s is missing its bearer security requirement", route.method, route.path)
		} else if _, ok := operation.Security[0]["BearerAuth"]; !ok {
			t.Errorf("%s %s does not reference BearerAuth", route.method, route.path)
		}
	}
	for _, schema := range []string{"Perfil", "UpdatePerfil", "Error", "GatewayError"} {
		if _, ok := spec.Definitions[schema]; !ok {
			t.Errorf("spec is missing schema %q", schema)
		}
	}
	var updateSchema struct {
		Required []string `json:"required"`
	}
	if err := json.Unmarshal(spec.Definitions["UpdatePerfil"], &updateSchema); err != nil {
		t.Fatalf("invalid UpdatePerfil schema: %v", err)
	}
	for _, field := range []string{"telefono", "direccion", "ciudad", "biografia"} {
		if !contains(updateSchema.Required, field) {
			t.Errorf("UpdatePerfil schema does not require %q", field)
		}
	}
	var putOperation struct {
		Parameters []struct {
			In       string `json:"in"`
			Required bool   `json:"required"`
			Schema   struct {
				Ref string `json:"$ref"`
			} `json:"schema"`
		} `json:"parameters"`
	}
	if err := json.Unmarshal(spec.Paths["/perfiles/{empleadoId}"]["put"], &putOperation); err != nil {
		t.Fatalf("invalid PUT operation: %v", err)
	}
	var requestBodyFound bool
	for _, parameter := range putOperation.Parameters {
		if parameter.In == "body" {
			requestBodyFound = true
			if !parameter.Required || parameter.Schema.Ref != "#/definitions/UpdatePerfil" {
				t.Errorf("PUT body should be required and use UpdatePerfil schema, got %#v", parameter)
			}
		}
	}
	if !requestBodyFound {
		t.Error("PUT operation is missing its request body")
	}
}

func TestOpenAPIDocUsesNativeBearerAuthAndCorrectUpdateBody(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodGet, "/openapi.json", nil)
	res := httptest.NewRecorder()
	r.ServeHTTP(res, req)

	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var spec struct {
		OpenAPI    string `json:"openapi"`
		Components struct {
			SecuritySchemes map[string]struct {
				Type   string `json:"type"`
				Scheme string `json:"scheme"`
			} `json:"securitySchemes"`
		} `json:"components"`
		Paths map[string]map[string]json.RawMessage `json:"paths"`
	}
	if err := json.Unmarshal(res.Body.Bytes(), &spec); err != nil {
		t.Fatalf("invalid OpenAPI JSON: %v", err)
	}
	if spec.OpenAPI != "3.0.3" {
		t.Fatalf("expected OpenAPI 3.0.3, got %q", spec.OpenAPI)
	}
	bearerScheme, ok := spec.Components.SecuritySchemes["BearerAuth"]
	if !ok || bearerScheme.Type != "http" || bearerScheme.Scheme != "bearer" {
		t.Fatalf("expected native HTTP bearer security scheme, got %#v", bearerScheme)
	}

	var putOperation struct {
		Security    []map[string][]string `json:"security"`
		RequestBody struct {
			Required bool `json:"required"`
			Content  map[string]struct {
				Schema struct {
					Ref string `json:"$ref"`
				} `json:"schema"`
				Example map[string]string `json:"example"`
			} `json:"content"`
		} `json:"requestBody"`
	}
	if err := json.Unmarshal(spec.Paths["/perfiles/{empleadoId}"]["put"], &putOperation); err != nil {
		t.Fatalf("invalid OpenAPI PUT operation: %v", err)
	}
	if len(putOperation.Security) == 0 {
		t.Fatal("PUT operation is missing its bearer security requirement")
	}
	if _, ok := putOperation.Security[0]["BearerAuth"]; !ok {
		t.Fatal("PUT operation does not reference BearerAuth")
	}
	if !putOperation.RequestBody.Required {
		t.Fatal("PUT request body should be required")
	}
	jsonBody, ok := putOperation.RequestBody.Content["application/json"]
	if !ok || jsonBody.Schema.Ref != "#/components/schemas/UpdatePerfil" {
		t.Fatalf("PUT request body should use UpdatePerfil schema, got %#v", jsonBody)
	}
	for _, field := range []string{"telefono", "direccion", "ciudad", "biografia"} {
		if jsonBody.Example[field] == "" {
			t.Errorf("PUT request example is missing %q", field)
		}
	}
}

func TestSwaggerUIUsesOpenAPIV3Document(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodGet, "/swagger/index.html", nil)
	res := httptest.NewRecorder()
	r.ServeHTTP(res, req)

	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", res.Code)
	}
	if !strings.Contains(res.Body.String(), "/openapi.json") {
		t.Error("Swagger UI is not configured to load the OpenAPI 3 document")
	}
}

func contains(values []string, value string) bool {
	for _, candidate := range values {
		if candidate == value {
			return true
		}
	}
	return false
}
