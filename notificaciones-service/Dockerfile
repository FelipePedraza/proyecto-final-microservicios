# ==========================================================================
# Dockerfile - notificaciones-service (Reto 4, Integrante 2)
#
# Construccion y ejecucion (desde esta carpeta):
#   docker build -t notificaciones-service .
#   docker run --rm -p 8082:8082 notificaciones-service
#
# Sigue el mismo patron de seguridad ya establecido en el resto del proyecto
# (Reto-1/RegistroService/Dockerfile, gateway-service/Dockerfile): build
# multi-stage y ejecucion como usuario sin privilegios (appuser, UID 10001).
# ==========================================================================

# ---------- Etapa 1: dependencias (solo produccion) ----------
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
# npm ci (no npm install): instala EXACTAMENTE lo que dice package-lock.json,
# reproducible entre maquinas. --omit=dev deja fuera jest/supertest: no hacen
# falta en la imagen de runtime.
RUN npm ci --omit=dev

# ---------- Etapa 2: runtime ----------
FROM node:20-alpine AS final
WORKDIR /app

RUN addgroup -g 10001 appgroup \
    && adduser -D -u 10001 -G appgroup -h /app appuser

COPY --from=deps --chown=appuser:appgroup /app/node_modules ./node_modules
COPY --chown=appuser:appgroup package.json ./
COPY --chown=appuser:appgroup src ./src

USER appuser

ENV NODE_ENV=production
ENV PORT=8082
EXPOSE 8082

# El healthcheck vive en docker-compose.yml (mismo patron que los otros Dockerfiles
# del proyecto: ninguno define HEALTHCHECK aqui, todos lo hacen en el compose).
CMD ["node", "src/server.js"]
