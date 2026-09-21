from pydantic import BaseModel, ConfigDict

class Department(BaseModel):
    
    id: str
    name: str
    description: str

    # Configuración para permitir la creación de instancias a partir de atributos de objetos
    model_config = ConfigDict(from_attributes=True)