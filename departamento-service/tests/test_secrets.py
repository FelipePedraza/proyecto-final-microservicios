import pytest

from app.config.secrets import read_secret


def test_read_secret_prefers_file(tmp_path, monkeypatch):
    (tmp_path / "db_password").write_text("valor-seguro\n", encoding="utf-8")
    monkeypatch.setenv("SECRETS_DIR", str(tmp_path))
    monkeypatch.setenv("DB_PASSWORD_TEST", "fallback")

    assert read_secret("db_password", "DB_PASSWORD_TEST") == "valor-seguro"


def test_read_secret_rejects_missing_value(tmp_path, monkeypatch):
    monkeypatch.setenv("SECRETS_DIR", str(tmp_path))
    monkeypatch.delenv("DB_PASSWORD_TEST", raising=False)

    with pytest.raises(RuntimeError) as exception:
        read_secret("db_password", "DB_PASSWORD_TEST")

    assert str(exception.value) == (
        "ERROR: Secreto 'db_password' no configurado. "
        "Montar /run/secrets/db_password o definir DB_PASSWORD_TEST."
    )


def test_read_secret_falls_back_to_environment(tmp_path, monkeypatch):
    monkeypatch.setenv("SECRETS_DIR", str(tmp_path))
    monkeypatch.setenv("DB_PASSWORD_TEST", "fallback-seguro")

    assert read_secret("db_password", "DB_PASSWORD_TEST") == "fallback-seguro"


def test_read_secret_rejects_empty_value_explicitly(tmp_path, monkeypatch):
    (tmp_path / "db_password").write_text("  \n", encoding="utf-8")
    monkeypatch.setenv("SECRETS_DIR", str(tmp_path))

    with pytest.raises(RuntimeError, match="ERROR:.*está vacío"):
        read_secret("db_password", "DB_PASSWORD_TEST")


@pytest.mark.parametrize("marker", ["REEMPLAZAR_CON_VALOR_REAL", "GESTIONADO_EN_SECRETS_DB"])
def test_read_secret_rejects_template_value_explicitly(tmp_path, monkeypatch, marker):
    (tmp_path / "db_password").write_text(marker, encoding="utf-8")
    monkeypatch.setenv("SECRETS_DIR", str(tmp_path))

    with pytest.raises(RuntimeError, match="ERROR:.*marcador de plantilla inseguro"):
        read_secret("db_password", "DB_PASSWORD_TEST")
