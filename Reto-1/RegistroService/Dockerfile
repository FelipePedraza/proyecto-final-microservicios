# ==========================================================================
# Dockerfile - RegistroService (Reto 1)
# Ubicación esperada: Reto-1/RegistroService/Dockerfile
#   (mismo nivel que RegistroService.sln)
#
# Comandos de construcción y ejecución (ejecutar desde esta carpeta):
#   docker build -t servidor-empleados .
#   docker run -p 8080:8080 servidor-empleados
#
# Prueba:
#   curl http://localhost:8080/empleados/E001
# ==========================================================================

# ---------- Etapa 1: Build (SDK completo, solo se usa para compilar) ----------
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copiamos primero solo el .csproj para aprovechar la cache de capas de Docker:
# si el código cambia pero las dependencias no, "dotnet restore" no se repite.
COPY ["RegistroService/RegistroService.csproj", "RegistroService/"]
RUN dotnet restore "RegistroService/RegistroService.csproj"

# Ahora copiamos el resto del código fuente
COPY . .

# Publicamos en modo Release (self-contained=false, usa el runtime de la imagen final)
WORKDIR /src/RegistroService
RUN dotnet publish "RegistroService.csproj" \
    -c Release \
    -o /app/publish \
    /p:UseAppHost=false

# ---------- Etapa 2: Runtime (solo el ASP.NET runtime, imagen liviana) ----------
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app

# Se usa para que Docker compruebe el endpoint HTTP de salud del servicio.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 appgroup \
    && useradd --uid 10001 --gid appgroup --home-dir /app --shell /usr/sbin/nologin --no-create-home appuser

# Copiamos únicamente los binarios publicados de la etapa anterior
COPY --from=build --chown=appuser:appgroup /app/publish .
USER appuser

# Kestrel debe escuchar en todas las interfaces del contenedor (0.0.0.0),
# no solo en localhost, para que el mapeo de puertos de "docker run -p" funcione.
ENV ASPNETCORE_URLS=http://+:8080
ENV ASPNETCORE_ENVIRONMENT=Production
# Evita el warning/telemetría de .NET en cada arranque
ENV DOTNET_NOLOGO=true
ENV DOTNET_CLI_TELEMETRY_OPTOUT=true

EXPOSE 8080

ENTRYPOINT ["dotnet", "RegistroService.dll"]
