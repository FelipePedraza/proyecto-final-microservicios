from app.repositories.department_repository import department_repository
from app.model.department_model import DepartmentModel
from app.schemas.departamento import Department
from sqlalchemy.exc import IntegrityError
from app.exceptions.exceptions import DepartamentoNoEncontradoError, DepartamentoYaExisteError
from sqlalchemy.orm import Session
from fastapi import HTTPException, status


class Department_service:

    def __init__(self, db: Session):
        self.db = db
        self.repo = department_repository(db)

    def create_department(self, department: Department):

        # validar que el departamento no exista antes de crearlo
        
        if self.repo.get_by_id(department.id) is not None:
            raise DepartamentoYaExisteError(department.id)
        try:
            return self.repo.save(DepartmentModel(
                id=department.id, name=department.name, description=department.description))
        except IntegrityError:
            # carrera entre dos peticiones concurrentes con el mismo id
            self.db.rollback()
            raise DepartamentoYaExisteError(department.id)
        

    def get_department(self, id: str):
        # Lógica para obtener un departamento por su ID desde la base de datos
        department = self.repo.get_by_id(id)
        if department is None:
            raise DepartamentoNoEncontradoError(id)
        return department


    def list_departments(self):
        # Lógica para listar todos los departamentos desde la base de datos
        return self.repo.get_all()