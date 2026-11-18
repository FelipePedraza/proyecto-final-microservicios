package config

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"
)

func TestRequiredSecretPrefersFile(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "test_secret"), []byte("valor-seguro\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("SECRETS_DIR", dir)
	t.Setenv("TEST_SECRET_VALUE", "fallback")

	if got := requiredSecret("test_secret", "TEST_SECRET_VALUE"); got != "valor-seguro" {
		t.Fatalf("expected file value, got %q", got)
	}
}

func TestRequiredSecretRejectsMissingValue(t *testing.T) {
	t.Setenv("SECRETS_DIR", t.TempDir())
	const environmentVariable = "TEST_SECRET_MISSING_VALUE"
	previous, existed := os.LookupEnv(environmentVariable)
	if err := os.Unsetenv(environmentVariable); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if existed {
			_ = os.Setenv(environmentVariable, previous)
		} else {
			_ = os.Unsetenv(environmentVariable)
		}
	})

	assertPanicsWith(t,
		`ERROR: Secreto "test_secret" no configurado. Montar /run/secrets/test_secret o definir TEST_SECRET_MISSING_VALUE.`,
		func() { requiredSecret("test_secret", environmentVariable) })
}

func TestRequiredSecretFallsBackToEnvironment(t *testing.T) {
	t.Setenv("SECRETS_DIR", t.TempDir())
	t.Setenv("TEST_SECRET_VALUE", "fallback-seguro")

	if got := requiredSecret("test_secret", "TEST_SECRET_VALUE"); got != "fallback-seguro" {
		t.Fatalf("expected environment fallback, got %q", got)
	}
}

func TestRequiredSecretRejectsTemplateMarker(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "test_secret"), []byte("GESTIONADO_EN_SECRETS_DB"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("SECRETS_DIR", dir)
	assertPanicsWith(t,
		`ERROR: Secreto "test_secret" contiene un marcador de plantilla inseguro. Montar /run/secrets/test_secret o definir TEST_SECRET_VALUE.`,
		func() { requiredSecret("test_secret", "TEST_SECRET_VALUE") })
}

func TestRequiredSecretRejectsEmptyValue(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "test_secret"), []byte("  \n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("SECRETS_DIR", dir)

	assertPanicsWith(t,
		`ERROR: Secreto "test_secret" está vacío. Montar /run/secrets/test_secret o definir TEST_SECRET_VALUE.`,
		func() { requiredSecret("test_secret", "TEST_SECRET_VALUE") })
}

func assertPanicsWith(t *testing.T, expected string, action func()) {
	t.Helper()
	defer func() {
		panicValue := recover()
		if panicValue == nil {
			t.Fatal("expected action to panic")
		}
		if actual := fmt.Sprint(panicValue); actual != expected {
			t.Fatalf("unexpected panic message\nwant: %s\n got: %s", expected, actual)
		}
	}()
	action()
}
