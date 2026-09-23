package domain

import "time"

type Perfil struct {
	ID             string     `json:"id"`
	EmpleadoID     string     `json:"empleadoId"`
	Nombre         string     `json:"nombre"`
	Email          string     `json:"email"`
	Telefono       string     `json:"telefono"`
	Direccion      string     `json:"direccion"`
	Ciudad         string     `json:"ciudad"`
	Biografia      string     `json:"biografia"`
	Archivado      bool       `json:"archivado"`
	FechaCreacion  time.Time  `json:"fechaCreacion"`
	FechaArchivado *time.Time `json:"fechaArchivado,omitempty"`
}

type UpdatePerfil struct {
	Telefono  string `json:"telefono"`
	Direccion string `json:"direccion"`
	Ciudad    string `json:"ciudad"`
	Biografia string `json:"biografia"`
}
