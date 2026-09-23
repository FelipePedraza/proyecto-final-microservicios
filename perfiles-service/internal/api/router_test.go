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
func (f *fakeRepository) List(context.Context) ([]domain.Perfil, error) { return []domain.Perfil{f.profile}, nil }
func (f *fakeRepository) Get(_ context.Context, id string) (domain.Perfil, error) { if id != f.profile.EmpleadoID { return domain.Perfil{}, repository.ErrNotFound }; return f.profile,nil }
func (f *fakeRepository) Update(_ context.Context, id string, u domain.UpdatePerfil) (domain.Perfil,error) { if id != f.profile.EmpleadoID{return domain.Perfil{},repository.ErrNotFound}; f.profile.Telefono=u.Telefono; return f.profile,nil }
func (*fakeRepository) Ready(context.Context) error { return nil }

func TestGetProfileReturns404(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodGet, "/perfiles/E999", nil)
	res := httptest.NewRecorder(); r.ServeHTTP(res, req)
	if res.Code != http.StatusNotFound { t.Fatalf("expected 404, got %d", res.Code) }
}

func TestPutProfileRejectsInvalidJSON(t *testing.T) {
	r := NewRouter(&fakeRepository{profile: domain.Perfil{EmpleadoID: "E001"}}, slog.Default())
	req := httptest.NewRequest(http.MethodPut, "/perfiles/E001", strings.NewReader(`{"telefono":42}`))
	res := httptest.NewRecorder(); r.ServeHTTP(res, req)
	if res.Code != http.StatusBadRequest { t.Fatalf("expected 400, got %d", res.Code) }
	var body map[string]string
	if err := json.Unmarshal(res.Body.Bytes(), &body); err != nil || body["error"] == "" { t.Fatalf("unexpected error body: %s", res.Body.String()) }
}
