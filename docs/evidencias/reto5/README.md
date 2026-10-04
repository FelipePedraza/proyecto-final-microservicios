# Reto 5: Seguridad y Control de Acceso con JWT (auth-service)

Este documento detalla la implementación del servicio de autenticación (uth-service) desarrollado por el **Integrante 1**, así como las decisiones arquitectónicas clave tomadas para cumplir con los requerimientos del Reto 5.

## 1. Estrategia de Validación JWT (Arquitectura Centralizada)

En lugar de crear un JWTFilter en cada microservicio (lo cual duplicaría código y rompería el principio de responsabilidad única), hemos adoptado una **arquitectura centralizada a través del API Gateway**.

*   **Generación:** Solo el uth-service genera y firma los JWT.
*   **Validación:** El gateway-service intercepta todas las peticiones a rutas protegidas. Utilizando la misma clave secreta, el Gateway verifica matemáticamente la firma del token, su vigencia (exp) y el rol (ole).
*   **Propagación de Identidad:** Una vez validado, el Gateway elimina las cabeceras de identidad proporcionadas por el cliente e inyecta el ID y rol verificados en `X-User-Id` y `X-User-Role`. Después reenvía la petición al microservicio correspondiente, que no necesita importar librerías JWT.

## 2. Instrucciones: Cómo obtener un token (Flujo de Login)

Para obtener un Access JWT, se debe realizar una petición POST al endpoint público de login. 
*(Asegúrese de que el proyecto Spring Boot esté en ejecución en el puerto asignado)*.

**Endpoint:** POST /auth/login
**Headers:** Content-Type: application/json

**Body:**
``json
{
  "email": "admin@empresa.com",
  "password": "admin123"
}
``

**Respuesta Exitosa (200 OK):**
``json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWI..."
}
``

### El Usuario Administrador (Seed)
Al arrancar la aplicación por primera vez, un componente llamado AdminSeeder verifica si existe el administrador. Si no existe, crea automáticamente la cuenta dmin@empresa.com con contraseña dmin123 y rol ADMIN.

## 3. Manejo de Contraseñas y Seguridad
*   **Algoritmo:** Todas las contraseñas se almacenan fuertemente hasheadas usando BCryptPasswordEncoder de Spring Security. Nunca se guardan contraseñas en texto plano.
*   **Tokens Desechables:** Para recuperar contraseñas, no se envía la clave por correo. En su lugar, el sistema genera un **Reset Token** (validez de 15 minutos, con claim "type": "RESET_PASSWORD"). Este token viaja en el cuerpo del JSON (no como Bearer) hacia el endpoint POST /auth/reset-password.
*   **Clave Secreta JWT:** La clave secreta para firmar los tokens debe ser de al menos 256 bits (32 caracteres). Esta se configura vía variables de entorno en el archivo pplication.yaml o .env bajo la llave jwt.secret.

## 4. Gestión Global de Excepciones
Para garantizar un estándar en las respuestas de error y evitar que se filtren trazas de Java al cliente, se implementó un RestExceptionHandler (@RestControllerAdvice).
Este componente captura excepciones personalizadas (UnauthorizedException, ResourceNotFoundException, etc.) y validaciones de DTOs (@Valid), formateándolas en un ResponseDTO estándar.

## 5. Eventos y RabbitMQ (Preparación)
El servicio está preparado con:
*   EventEnvelope<T>: Estructura exigida por el catálogo de eventos.
*   ProcessedEvent: Tabla de deduplicación requerida para el patrón Inbox.
*   RabbitTemplate: Inyectado en los métodos de recuperación y reseteo para disparar alertas al 
otificaciones-service.
*(La configuración de colas y Listeners es responsabilidad del Integrante 3).*

## 6. Despliegue y Configuracion Docker
Como desarrollador de este microservicio, se incluye toda la configuracion necesaria para conectarlo al ecosistema general.

### Archivo .env
Agregue las siguientes lineas al archivo .env raiz del proyecto:
JWT_SECRET_KEY=SuperSecretaClaveDe256BitsMinimoParaQueFuncioneJJWTEnSpring2026!
AUTH_DB_NAME=auth_db
AUTH_DB_USER=postgres
AUTH_DB_PASSWORD=postgres
AUTH_SERVICE_URL=http://auth-service:8089

### docker-compose.yml
`auth-db` y `auth-service` están integrados en el `docker-compose.yml` global.
El puerto 8089 solo se expone dentro de la red de Docker; desde el host se debe
acceder a `/auth/**` mediante el Gateway en el puerto 8088.
