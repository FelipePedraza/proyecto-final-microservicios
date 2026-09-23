package api

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/domain"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/repository"
	"github.com/go-chi/chi/v5"
	httpSwagger "github.com/swaggo/http-swagger"
	_ "github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/docs"
)

type profileRepository interface {
	List(context.Context) ([]domain.Perfil, error)
	Get(context.Context, string) (domain.Perfil, error)
	Update(context.Context, string, domain.UpdatePerfil) (domain.Perfil, error)
	Ready(context.Context) error
}

func NewRouter(repo profileRepository, logger *slog.Logger) http.Handler {
	r:=chi.NewRouter(); r.Get("/health/live",func(w http.ResponseWriter,_ *http.Request){json.NewEncoder(w).Encode(map[string]string{"status":"healthy"})})
	r.Get("/health/ready",func(w http.ResponseWriter,req *http.Request){if err:=repo.Ready(req.Context());err!=nil{writeError(w,http.StatusServiceUnavailable,"database no disponible");return};json.NewEncoder(w).Encode(map[string]string{"status":"ready","database":"up"})})
	r.Get("/swagger/*",httpSwagger.WrapHandler); r.Get("/perfiles",list(repo)); r.Get("/perfiles/{empleadoId}",get(repo)); r.Put("/perfiles/{empleadoId}",update(repo)); return r
}
func list(repo profileRepository) http.HandlerFunc{return func(w http.ResponseWriter,r *http.Request){p,err:=repo.List(r.Context());if err!=nil{writeError(w,500,"no se pudieron consultar los perfiles");return};writeJSON(w,200,p)}}
func get(repo profileRepository) http.HandlerFunc{return func(w http.ResponseWriter,r *http.Request){p,err:=repo.Get(r.Context(),chi.URLParam(r,"empleadoId"));if errors.Is(err,repository.ErrNotFound){writeError(w,404,"no existe un perfil para el empleado solicitado");return};if err!=nil{writeError(w,500,"no se pudo consultar el perfil");return};writeJSON(w,200,p)}}
func update(repo profileRepository) http.HandlerFunc{return func(w http.ResponseWriter,r *http.Request){var u domain.UpdatePerfil; dec:=json.NewDecoder(r.Body);dec.DisallowUnknownFields();if dec.Decode(&u)!=nil{writeError(w,400,"body inválido");return};p,err:=repo.Update(r.Context(),chi.URLParam(r,"empleadoId"),u);if errors.Is(err,repository.ErrNotFound){writeError(w,404,"no existe un perfil para el empleado solicitado");return};if err!=nil{writeError(w,500,"no se pudo actualizar el perfil");return};writeJSON(w,200,p)}}
func writeJSON(w http.ResponseWriter,status int,v any){w.Header().Set("Content-Type","application/json");w.WriteHeader(status);_ = json.NewEncoder(w).Encode(v)}
func writeError(w http.ResponseWriter,status int,msg string){writeJSON(w,status,map[string]string{"error":msg})}
