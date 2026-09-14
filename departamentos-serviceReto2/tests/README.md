# Pruebas del microservicio de departamentos

Este directorio contiene la batería de pruebas del servicio de departamentos del Reto 2.

## Objetivo

Validar que el microservicio:

- responde correctamente a `/health`
- crea departamentos con el formato esperado
- recupera un departamento por su ID
- lista todos los departamentos
- devuelve `404` cuando el departamento no existe

## Estructura de pruebas

### 1) `conftest.py`
Archivo de configuración y fixtures compartidos para todas las pruebas.

Responsabilidades:

- prepara una base de datos SQLite en memoria para pruebas
- crea el schema de la aplicación antes de cada prueba
- limpia la base tras cada ejecución
- sobrescribe la dependencia `get_db` para que el cliente de pruebas use la sesión de test
- permite ejecutar la app con `TestClient` sin levantar Docker

### 2) `test_departamentos.py`
Archivo principal con los casos de prueba del servicio.

Casos cubiertos:

- `test_health_endpoint`: valida el endpoint de salud del servicio
- `test_create_and_get_department`: crea un departamento y luego lo consulta por ID
- `test_list_departments_returns_created_items`: crea un departamento y valida que aparece en la lista
- `test_get_missing_department_returns_404`: comprueba que un ID inexistente responde con `404`

## Cómo ejecutar las pruebas

Desde la carpeta del servicio:

```powershell
cd "C:\Users\DANIEL-PC\Documents\MIcro\proyecto-final-microservicios\departamentos-serviceReto2"
python -m pytest tests -q
```

También puedes ejecutar una prueba puntual:

```powershell
cd "C:\Users\DANIEL-PC\Documents\MIcro\proyecto-final-microservicios\departamentos-serviceReto2"
python -m pytest tests/test_departamentos.py -q
```

## Requisitos previos

Asegúrate de tener instaladas las dependencias del proyecto y `pytest`:

```powershell
cd "C:\Users\DANIEL-PC\Documents\MIcro\proyecto-final-microservicios\departamentos-serviceReto2"
python -m pip install -r requirements.txt
python -m pip install pytest httpx
```

## Resultado esperado

Ejecutando la suite, el resultado esperado es:

```text
4 passed, 1 warning in 0.14s
```

La advertencia es un `DeprecationWarning` de Starlette/TestClient y no afecta la funcionalidad ni la validez de las pruebas.

## Nota de entorno

Estas pruebas no requieren Docker ni PostgreSQL; usan SQLite en memoria para validar el comportamiento del servicio de forma rápida y aislada.
