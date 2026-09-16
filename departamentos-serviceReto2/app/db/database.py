
import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

DB_USER = os.getenv("DB_DEPARTAMENTOS_USER", "postgres")
DB_PASSWORD = os.getenv("DB_DEPARTAMENTOS_PASSWORD", "postgres")
DB_HOST = os.getenv("DB_DEPARTAMENTOS_HOST", "localhost")
DB_PORT = os.getenv("DB_DEPARTAMENTOS_PORT", "5432")
DB_NAME = os.getenv("DB_DEPARTAMENTOS_NAME", "departamentos_db")

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