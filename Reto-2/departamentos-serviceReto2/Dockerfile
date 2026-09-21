## primero la version de python
FROM python:3.11

# EL DIRECTORIO DE TRABAJO DE DOCKER
WORKDIR /app

# LAS DEPENDENCIAS
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# el codigo
COPY . .

# EXPONER EL PUERTO
EXPOSE 8081

# COMANDO PARA EJECUTAR LA APLICACION
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8081"]