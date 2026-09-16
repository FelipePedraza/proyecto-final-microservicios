
# DEFINIMOS QUE VA A SER EL CONTROLADOR (ROUTER )
from app.schemas.departamento import Department
from app.service.department_service import Department_service
from fastapi import APIRouter, Depends, status
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
from app.db.database import get_db


router = APIRouter(prefix="/departamentos", tags=["Departamentos"])


# registrar un departamento 
@router.post("", response_model=Department, status_code=status.HTTP_201_CREATED)
def create_department(department: Department, db: Session = Depends(get_db)):
    return Department_service(db).create_department(department)

# consultar un departamento por su id
@router.get("/{id}", response_model=Department, status_code=status.HTTP_200_OK)
def get_department(id: str, db: Session = Depends(get_db)):
    return Department_service(db).get_department(id)

# listar todos los departamentos
@router.get("", response_model=list[Department], status_code=status.HTTP_200_OK)
def list_departments(db: Session = Depends(get_db)):
    return Department_service(db).list_departments()