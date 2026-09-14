from sqlalchemy.orm import Session
from app.model.department_model import DepartmentModel

class department_repository:
    def __init__(self, db: Session):
        self.db = db

    def save(self, department: DepartmentModel):
        self.db.add(department)
        self.db.commit()
        self.db.refresh(department)
        return department

    def get_by_id(self, id: str):
        return self.db.query(DepartmentModel).filter(DepartmentModel.id == id).first()

    def get_all(self):
        return self.db.query(DepartmentModel).all()