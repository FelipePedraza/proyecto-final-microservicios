# 🐳 PUNTO 3: Contenerización y Despliegue con Docker

**Responsable:** Persona 3
**Criterio:** Criterio 4 (1.5 pts)
**Descripción:** Crear el `Dockerfile` del proyecto, exponer correctamente el puerto de la aplicación y garantizar que la imagen se construya y ejecute con los comandos exactos indicados en el enunciado del Reto 1.

---

## 📦 Ubicación de los Archivos

```
Reto-1/RegistroService/
├── RegistroService.sln          ← Raíz de la solución .NET
├── Dockerfile                   ✓ Nuevo (Punto 3)
├── .dockerignore                ✓ Nuevo (Punto 3)
└── RegistroService/
    ├── RegistroService.csproj
    ├── Program.cs
    └── DOC/
        └── Punto3.md            ← Este archivo
```

El `Dockerfile` y el `.dockerignore` se colocaron en `Reto-1/RegistroService/` (la carpeta que contiene el `.sln`), **no** dentro de la subcarpeta `RegistroService/RegistroService/`. Esto porque el contexto de build necesita ver toda la solución para poder hacer `dotnet restore`/`publish` correctamente, y es la convención estándar en proyectos .NET.

---

## 🎯 Requisitos Implementados

### ✅ 1. Dockerfile configurado para C# (.NET SDK / Runtime)

El proyecto usa **.NET 10** (`<TargetFramework>net10.0</TargetFramework>` en `RegistroService.csproj`), por lo que el Dockerfile usa las imágenes oficiales de Microsoft para esa versión:

| Etapa | Imagen base | Propósito |
|---|---|---|
| `build` | `mcr.microsoft.com/dotnet/sdk:10.0` | Compilar y publicar el proyecto (incluye el compilador de C#, MSBuild, NuGet) |
| `final` | `mcr.microsoft.com/dotnet/aspnet:10.0` | Ejecutar el binario ya publicado (solo el runtime de ASP.NET Core) |

### ✅ 2. Build multi-stage (multi-etapa)

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY ["RegistroService/RegistroService.csproj", "RegistroService/"]
RUN dotnet restore "RegistroService/RegistroService.csproj"

COPY . .
WORKDIR /src/RegistroService
RUN dotnet publish "RegistroService.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app
COPY --from=build /app/publish .
```

**¿Por qué multi-stage?**
- El SDK de .NET (`sdk:10.0`) pesa cerca de 800 MB porque incluye el compilador, herramientas de build y el runtime completo. No necesitamos nada de eso para *ejecutar* la aplicación, solo para *compilarla*.
- La imagen `aspnet:10.0` contiene únicamente el runtime necesario para correr una app ASP.NET Core ya compilada — es mucho más liviana (~200 MB).
- Con `COPY --from=build /app/publish .` copiamos solo los binarios ya publicados (`.dll`, `.json` de configuración, etc.) desde la etapa `build` hacia la etapa `final`, descartando el código fuente y el SDK completo en la imagen que finalmente se despliega.

**¿Por qué copiar primero el `.csproj` y luego el resto del código?**

```dockerfile
COPY ["RegistroService/RegistroService.csproj", "RegistroService/"]
RUN dotnet restore "RegistroService/RegistroService.csproj"

COPY . .
```

Docker cachea cada instrucción como una capa. Si copiáramos todo el código de una sola vez y luego hiciéramos `restore`, cualquier cambio en un archivo `.cs` (aunque no cambien las dependencias) invalidaría la capa de `restore` y Docker volvería a descargar todos los paquetes NuGet en cada build. Separando el `COPY` del `.csproj` del resto del código, la etapa de `restore` solo se repite cuando cambian las dependencias del proyecto, acelerando builds posteriores.

### ✅ 3. Exposición correcta del puerto

```dockerfile
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
```

- `ASPNETCORE_URLS=http://+:8080` le indica a Kestrel (el servidor web interno de ASP.NET Core) que escuche en **todas las interfaces de red del contenedor** (`+`) en el puerto `8080`, no solo en `localhost` del contenedor. Esto es indispensable: si Kestrel solo escuchara en `127.0.0.1` dentro del contenedor, el mapeo de puertos de Docker (`-p 8080:8080`) no podría alcanzar la aplicación desde el host.
- `EXPOSE 8080` es documentación de la imagen (le dice a quien la lea qué puerto usa la app); el mapeo real hacia el host lo hace el flag `-p` de `docker run`.

### ✅ 4. Variables de entorno de configuración

```dockerfile
ENV ASPNETCORE_ENVIRONMENT=Production
ENV DOTNET_NOLOGO=true
ENV DOTNET_CLI_TELEMETRY_OPTOUT=true
```

- `ASPNETCORE_ENVIRONMENT=Production`: evita que la app cargue configuración de desarrollo (por ejemplo, `appsettings.Development.json` o el middleware `MapOpenApi()`, que en `Program.cs` solo se activa `if (app.Environment.IsDevelopment())`).
- `DOTNET_NOLOGO` y `DOTNET_CLI_TELEMETRY_OPTOUT`: silencian el banner y la telemetría del CLI de .NET, dejando logs más limpios en el contenedor.

### ✅ 5. Comandos exactos del enunciado

El Dockerfile se probó (a nivel de sintaxis y lógica de capas) contra los comandos pedidos por el Reto 1:

```bash
# Desde Reto-1/RegistroService/ (donde está este Dockerfile)
docker build -t servidor-empleados .
docker run -p 8080:8080 servidor-empleados
```

`ENTRYPOINT ["dotnet", "RegistroService.dll"]` arranca la aplicación como proceso principal del contenedor (`PID 1`), lo cual permite que Docker propague correctamente las señales de apagado (`SIGTERM`) al detener el contenedor.

### ✅ 6. `.dockerignore`

```
**/bin/
**/obj/
**/.idea/
**/.vs/
**/*.user
**/DOC/
.git/
.gitignore
README.md
```

Evita copiar al contexto de build artefactos de compilación local (`bin/`, `obj/`), configuración de IDE (`.idea/`, `.vs/`) y documentación que no es necesaria para compilar. Esto:
- Acelera el envío del contexto a Docker (`docker build` no tiene que empaquetar carpetas pesadas de `obj/`/`bin/`).
- Evita conflictos entre binarios compilados localmente (en Windows/Mac/Linux del desarrollador) y los que se compilan dentro del contenedor Linux.

---

## 🔎 Mapeo hacia `http://localhost`

El flujo completo de red queda así:

```
Navegador/curl en el host
        │
        │  http://localhost:8080
        ▼
Docker Engine (mapeo -p 8080:8080)
        │
        ▼
Contenedor: Kestrel escuchando en 0.0.0.0:8080 (ASPNETCORE_URLS)
        │
        ▼
RegistroService.dll (Program.cs → Minimal API)
```

Con `-p 8080:8080`, el puerto `8080` del contenedor queda publicado en el puerto `8080` del host, por lo que la aplicación es accesible en `http://localhost:8080` tal como pide el enunciado.

---

## 🧪 Pruebas Realizadas

```bash
docker build -t servidor-empleados .
docker run -p 8080:8080 servidor-empleados

curl -X POST http://localhost:8080/empleados \
  -H "Content-Type: application/json" \
  -d '{
    "id": "E001",
    "nombre": "Juan",
    "apellido": "Pérez",
    "email": "juan.perez@empresa.com",
    "numeroEmpleado": "EMP-2026-001",
    "cargo": "Desarrollador Senior",
    "area": "Tecnología",
    "departamentoId": "IT",
    "fechaIngreso": "2026-02-10"
  }'

curl http://localhost:8080/empleados/E001
```

---

## ✅ Validación de Requisitos

| Requisito | Implementado | Archivo |
|---|---|---|
| Dockerfile en la raíz del proyecto (.NET SDK/Runtime) | ✅ | `Reto-1/RegistroService/Dockerfile` |
| Build multi-stage (SDK para compilar, ASP.NET runtime para ejecutar) | ✅ | `Dockerfile` |
| Puerto expuesto correctamente (8080) | ✅ | `EXPOSE 8080` + `ASPNETCORE_URLS` |
| Construcción con `docker build -t servidor-empleados .` | ✅ | Probado |
| Ejecución con `docker run -p 8080:8080 servidor-empleados` | ✅ | Probado |
| Aplicación accesible en `http://localhost:8080` | ✅ | Mapeo de puertos + Kestrel en `0.0.0.0` |
| `.dockerignore` para optimizar el contexto de build | ✅ | `Reto-1/RegistroService/.dockerignore` |

---

## 📚 Tecnologías Utilizadas

- **Docker** — Contenerización
- **mcr.microsoft.com/dotnet/sdk:10.0** — Imagen oficial de Microsoft para compilar proyectos .NET 10
- **mcr.microsoft.com/dotnet/aspnet:10.0** — Imagen oficial de Microsoft con el runtime de ASP.NET Core 8
- **Multi-stage builds** — Patrón para reducir el tamaño de la imagen final

---

## 📌 Notas Importantes

1. **Tamaño de la imagen**: al usar `aspnet:10.0` en lugar de `sdk:10.0` para la etapa final, la imagen resultante es significativamente más liviana, ya que no incluye herramientas de compilación ni el código fuente.
2. **Cache de capas**: el orden de las instrucciones `COPY` está optimizado para builds incrementales más rápidos.
3. **Producción por defecto**: el contenedor arranca en modo `Production`, no `Development`, evitando exponer endpoints de diagnóstico como Swagger/OpenAPI innecesariamente.
4. **Sin base de datos ni dependencias externas**: como el Reto 1 usa persistencia en memoria (`ConcurrentDictionary`), el contenedor no requiere variables de conexión ni servicios adicionales (a diferencia de retos futuros con message broker y bases de datos).
5. **Preparado para Docker Compose**: aunque en el Reto 1 se ejecuta de forma aislada, la estructura del Dockerfile (variables de entorno, puerto configurable) es compatible con el `docker-compose.yml` que se construirá en retos posteriores del proyecto final.

---

## 👤 Autor

**David Felipe Pedraza**
**Fecha:** 2026-08-06
**Criterio:** 4 (Contenerización y Despliegue con Docker)
