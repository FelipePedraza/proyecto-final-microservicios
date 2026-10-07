from app.db.database import Base
from sqlalchemy import Column, String



class DepartmentModel(Base):
    __tablename__ = "departments"

    id = Column(String, primary_key=True, index=True)
    name = Column(String, nullable=False)
    description = Column(String, nullable=True)