from pydantic import BaseModel, ConfigDict

class Department(BaseModel):
    id: str = Field(min_length=1, max_length=50)
    name: str = Field(min_length=1, max_length=150)
    description: str | None = None      # la columna es NULLABLE en init.sql
    # Configuración para permitir la creación de instancias a partir de atributos de objetos
    model_config = ConfigDict(from_attributes=True)