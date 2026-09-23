package messaging

import "testing"

func TestParseAcceptsPascalCase(t *testing.T) {
	event, err := parse([]byte(`{"Id":"evt-1","Type":"empleado.creado","Data":{"Id":"E001","Nombre":"Ana","Apellido":"Pérez","Email":"ana@example.com"}}`))
	if err != nil || event.ID != "evt-1" || event.Type != "empleado.creado" || text(event.Data, "Email", "email") != "ana@example.com" {
		t.Fatalf("unexpected parsed event: %#v, %v", event, err)
	}
}

func TestParseAcceptsCamelCase(t *testing.T) {
	event, err := parse([]byte(`{"id":"evt-2","type":"empleado.actualizado","data":{"empleadoId":"E002","nombre":"Luis","email":"luis@example.com"}}`))
	if err != nil || event.ID != "evt-2" || text(event.Data, "id", "empleadoId") != "E002" {
		t.Fatalf("unexpected parsed event: %#v, %v", event, err)
	}
}

func TestParseRejectsIncompleteEnvelope(t *testing.T) {
	if _, err := parse([]byte(`{"id":"evt-3","type":"empleado.creado","data":{}}`)); err == nil {
		t.Fatal("expected incomplete envelope error")
	}
}
