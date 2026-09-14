
# DEFINIMOS QUE VA A SER EL CONTROLADOR (ROUTER )
from app.schemas.departamento import Department
from app.service.department_service import Department_service
from fastapi import APIRouter, Depends, status
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
from app.db.database import get_db


router = APIRouter()


# registrar un departamento 
@router.post("/departamentos", response_model=Department, status_code=status.HTTP_201_CREATED)
def create_department(department: Department, db: Session = Depends(get_db)):

    #try:
         service = Department_service(db)
         result = service.create_department(department)
         return result
    
   # except Exception as e:
     #    return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"Could not create department": str(e)})


# consultar un departamento por su id
@router.get("/departamentos/{id}", response_model=Department, status_code=status.HTTP_200_OK)
def get_department(id: str, db: Session = Depends(get_db)):
    try:

        service = Department_service(db)
        department = service.get_department(id)
        if department is None:
            return JSONResponse(status_code=status.HTTP_404_NOT_FOUND, content={"Department not found": f"Department with id {id} not found"})
        
        return department

    except Exception as e:
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"Could not retrieve department": str(e)})


# lisrtar todos los departamentos
@router.get("/departamentos", response_model=list[Department], status_code=status.HTTP_200_OK)
def list_departments(db: Session = Depends(get_db)):

    try:
        service = Department_service(db)
        departments = service.list_departments()
        return departments

    except Exception as e:
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"Could not retrieve departments": str(e)})