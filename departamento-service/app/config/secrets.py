import os
from pathlib import Path


def read_secret(secret_name: str, env_fallback: str) -> str:
    secrets_dir = Path(os.getenv("SECRETS_DIR", "/run/secrets"))
    secret_path = secrets_dir / secret_name

    if secret_path.exists():
        try:
            value = secret_path.read_text(encoding="utf-8").strip()
        except OSError as exc:
            raise RuntimeError(
                f"ERROR: No se pudo leer el secreto '{secret_name}' desde {secret_path}."
            ) from exc
    else:
        environment_value = os.getenv(env_fallback)
        value = environment_value.strip() if environment_value is not None else None

    hint = (
        f" Montar /run/secrets/{secret_name} o definir {env_fallback}."
    )
    if value is None:
        raise RuntimeError(
            f"ERROR: Secreto '{secret_name}' no configurado.{hint}"
        )
    if not value:
        raise RuntimeError(f"ERROR: Secreto '{secret_name}' está vacío.{hint}")
    if value.startswith(("REEMPLAZAR", "GESTIONADO_EN_SECRETS")):
        raise RuntimeError(
            f"ERROR: Secreto '{secret_name}' contiene un marcador de plantilla inseguro.{hint}"
        )
    return value
