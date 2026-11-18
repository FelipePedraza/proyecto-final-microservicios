
import os
from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

def read_secret(secret_name: str, env_name: str) -> str:
    secret_path = Path(f"/run/secrets/{secret_name}")
    if secret_path.exists():
        return secret_path.read_text(encoding="utf-8").strip()
    value = os.getenv(env_name)
    if value:
        return value
    raise RuntimeError(
        f"Secreto '{secret_name}' no configurado. "
        f"Montar /run/secrets/{secret_name} o definir {env_name}."
    )

DB_USER = os.getenv("DB_DEPARTAMENTO_USER", "postgres")
DB_PASSWORD = read_secret("db_departamentos_password", "DB_DEPARTAMENTO_PASSWORD")
DB_HOST = os.getenv("DB_DEPARTAMENTO_HOST", "localhost")
DB_PORT = os.getenv("DB_DEPARTAMENTO_PORT", "5432")
DB_NAME = os.getenv("DB_DEPARTAMENTO_NAME", "departamento_db")

DATABASE_URL = f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,        # verifica la conexión antes de usarla: evita errores fantasma tras reiniciar la BD
    pool_recycle=1800,
    connect_args={"connect_timeout": 3},   # no colgarse 30 s esperando a un host muerto
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

def get_db():
    db = SessionLocal()
    try:
        yield db
    except Exception:
        db.rollback()          # faltaba: una sesión con transacción fallida no debe devolverse al pool
        raise
    finally:
        db.close()