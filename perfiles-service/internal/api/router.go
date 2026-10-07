package api

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"time"

	profiledocs "github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/docs"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/domain"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/repository"
	"github.com/go-chi/chi/v5"
	httpSwagger "github.com/swaggo/http-swagger"
)

type profileRepository interface {
	List(context.Context) ([]domain.Perfil, error)
	Get(context.Context, string) (domain.Perfil, error)
	Update(context.Context, string, domain.UpdatePerfil) (domain.Perfil, error)
	Ready(context.Context) error
}

func NewRouter(repo profileRepository, logger *slog.Logger) http.Handler {
	r := chi.NewRouter()
	r.Get("/health/live", func(w http.ResponseWriter, _ *http.Request) {
		json.NewEncoder(w).Encode(map[string]string{"status": "healthy"})
	})
	r.Get("/health/ready", func(w http.ResponseWriter, req *http.Request) {
		if err := repo.Ready(req.Context()); err != nil {
			writeError(w, http.StatusServiceUnavailable, "database no disponible")
			return
		}
		json.NewEncoder(w).Encode(map[string]string{"status": "ready", "database": "up"})
	})
	r.Get("/openapi.json", func(w http.ResponseWriter, req *http.Request) {
		http.ServeContent(w, req, "openapi.json", time.Time{}, bytes.NewReader(profiledocs.OpenAPIV3))
	})
	r.Get("/swagger/*", httpSwagger.Handler(httpSwagger.URL("/openapi.json")))
	r.Get("/perfiles", list(repo))
	r.Get("/perfiles/{empleadoId}", get(repo))
	r.Put("/perfiles/{empleadoId}", update(repo))
	return r
}
func list(repo profileRepository) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		p, err := repo.List(r.Context())
		if err != nil {
			writeError(w, 500, "no se pudieron consultar los perfiles")
			return
		}
		writeJSON(w, 200, p)
	}
}
func get(repo profileRepository) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		p, err := repo.Get(r.Context(), chi.URLParam(r, "empleadoId"))
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, 404, "no existe un perfil para el empleado solicitado")
			return
		}
		if err != nil {
			writeError(w, 500, "no se pudo consultar el perfil")
			return
		}
		writeJSON(w, 200, p)
	}
}
func update(repo profileRepository) http.HandlerFunc {
	type updateRequest struct {
		Telefono  *string `json:"telefono"`
		Direccion *string `json:"direccion"`
		Ciudad    *string `json:"ciudad"`
		Biografia *string `json:"biografia"`
	}

	return func(w http.ResponseWriter, r *http.Request) {
		var request updateRequest
		dec := json.NewDecoder(r.Body)
		dec.DisallowUnknownFields()
		if err := dec.Decode(&request); err != nil {
			writeError(w, http.StatusBadRequest, "body inválido")
			return
		}
		if request.Telefono == nil || request.Direccion == nil ||
			request.Ciudad == nil || request.Biografia == nil {
			writeError(w, http.StatusBadRequest, "se requieren telefono, direccion, ciudad y biografia")
			return
		}

		u := domain.UpdatePerfil{
			Telefono:  *request.Telefono,
			Direccion: *request.Direccion,
			Ciudad:    *request.Ciudad,
			Biografia: *request.Biografia,
		}
		p, err := repo.Update(r.Context(), chi.URLParam(r, "empleadoId"), u)
		if errors.Is(err, repository.ErrNotFound) {
			writeError(w, http.StatusNotFound, "no existe un perfil para el empleado solicitado")
			return
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, "no se pudo actualizar el perfil")
			return
		}
		writeJSON(w, http.StatusOK, p)
	}
}
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
