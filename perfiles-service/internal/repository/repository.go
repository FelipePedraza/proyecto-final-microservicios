package repository

import (
	"context"
	"errors"
	"time"

	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/config"
	"github.com/FelipePedraza/proyecto-final-microservicios/perfiles-service/internal/domain"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("perfil no encontrado")

type Repository struct{ pool *pgxpool.Pool }
func New(ctx context.Context, cfg config.Config) (*Repository, error) {
	pool, err := pgxpool.New(ctx, cfg.DatabaseURL()); if err != nil { return nil, err }
	if err = pool.Ping(ctx); err != nil { pool.Close(); return nil, err }
	return &Repository{pool: pool}, nil
}
func (r *Repository) Close() { r.pool.Close() }
func (r *Repository) Ready(ctx context.Context) error { return r.pool.Ping(ctx) }

func (r *Repository) List(ctx context.Context) ([]domain.Perfil, error) {
	rows, err := r.pool.Query(ctx, `SELECT id, empleado_id, nombre, email, telefono, direccion, ciudad, biografia, archivado, fecha_creacion, fecha_archivado FROM perfiles ORDER BY empleado_id`)
	if err != nil { return nil, err }; defer rows.Close()
	var result []domain.Perfil
	for rows.Next() { var p domain.Perfil; if err := rows.Scan(&p.ID,&p.EmpleadoID,&p.Nombre,&p.Email,&p.Telefono,&p.Direccion,&p.Ciudad,&p.Biografia,&p.Archivado,&p.FechaCreacion,&p.FechaArchivado); err != nil { return nil, err }; result = append(result,p) }
	return result, rows.Err()
}
func (r *Repository) Get(ctx context.Context, empleadoID string) (domain.Perfil, error) {
	var p domain.Perfil
	err := r.pool.QueryRow(ctx, `SELECT id, empleado_id, nombre, email, telefono, direccion, ciudad, biografia, archivado, fecha_creacion, fecha_archivado FROM perfiles WHERE empleado_id=$1`, empleadoID).Scan(&p.ID,&p.EmpleadoID,&p.Nombre,&p.Email,&p.Telefono,&p.Direccion,&p.Ciudad,&p.Biografia,&p.Archivado,&p.FechaCreacion,&p.FechaArchivado)
	if errors.Is(err, pgx.ErrNoRows) { return p, ErrNotFound }; return p, err
}
func (r *Repository) Update(ctx context.Context, id string, u domain.UpdatePerfil) (domain.Perfil, error) {
	_, err := r.pool.Exec(ctx, `UPDATE perfiles SET telefono=$2,direccion=$3,ciudad=$4,biografia=$5 WHERE empleado_id=$1`, id,u.Telefono,u.Direccion,u.Ciudad,u.Biografia); if err != nil { return domain.Perfil{},err }; return r.Get(ctx,id)
}
func (r *Repository) Process(ctx context.Context, eventID, eventType, empleadoID, nombre, email string) (bool, error) {
	tx, err := r.pool.Begin(ctx); if err != nil { return false,err }; defer tx.Rollback(ctx)
	var inserted bool
	err = tx.QueryRow(ctx, `INSERT INTO eventos_procesados(id) VALUES($1) ON CONFLICT DO NOTHING RETURNING true`, eventID).Scan(&inserted)
	if errors.Is(err, pgx.ErrNoRows) { return false, tx.Commit(ctx) }; if err != nil { return false,err }
	switch eventType {
	case "empleado.creado":
		_, err = tx.Exec(ctx, `INSERT INTO perfiles(id,empleado_id,nombre,email) VALUES($1,$2,$3,$4) ON CONFLICT (empleado_id) DO NOTHING`, uuid.New(),empleadoID,nombre,email)
	case "empleado.actualizado":
		_, err = tx.Exec(ctx, `UPDATE perfiles SET nombre=COALESCE(NULLIF($2,''),nombre),email=COALESCE(NULLIF($3,''),email) WHERE empleado_id=$1`, empleadoID, nombre, email)
	case "empleado.retirado":
		_, err = tx.Exec(ctx, `UPDATE perfiles SET archivado=true,fecha_archivado=COALESCE(fecha_archivado,$2) WHERE empleado_id=$1`, empleadoID,time.Now().UTC())
	}
	if err != nil { return false,err }; return true, tx.Commit(ctx)
}
