from fastapi import FastAPI
from app.api.departments_api import router as departamentos_router

app = FastAPI(
    title="Departamentos Service",
    description="Microservicio de gestión de departamentos",
    version="1.0.0"
)

app.include_router(departamentos_router)

# Requisito: Manejo universal de rutas no soportadas (404)
@app.exception_handler(404)
async def custom_404_handler(request, exc):
    return JSONResponse(
        status_code=status.HTTP_404_NOT_FOUND,
        content="Recurso no encontrado"
    )