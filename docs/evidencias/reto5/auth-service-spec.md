# Documentación: Auth Service (Gestión de Credenciales y Seguridad)

Este documento define la arquitectura, el modelo de datos, la integración por eventos y los contratos de la API para el microservicio de autenticación (`auth-service`), basado estrictamente en las reglas del **Reto 5**. 

---

## 1. Arquitectura y Responsabilidades (Integrante 1)

*   **Proveedor de Identidad:** Único servicio responsable de verificar contraseñas y emitir tokens JWT.
*   **Almacenamiento Seguro:** Contraseñas hasheadas siempre con **BCrypt**. Cero almacenamiento en texto plano.
*   **Validación Externa:** Este servicio *firma* el JWT. La *validación* (para bloquear o permitir peticiones) la hará el **API Gateway** (Integrante 2).
*   **Integración por Eventos:** El servicio no envía correos; emite eventos para que el `notificaciones-service` lo haga (Integrante 3).

---

## 2. Modelo de Datos (Base de Datos)

La entidad principal será `Account` (Cuenta). 

| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `id` | String (UUID) | Identificador único de la cuenta (debe coincidir con el `id` del empleado). |
| `email` | String | Correo electrónico (único). Se usa como `username` en el Login. |
| `password` | String | Hash BCrypt. Al crearse la cuenta por evento, este campo estará vacío o nulo hasta que se active. |
| `role` | String | Rol RBAC: `ADMIN` o `USER`. |
| `status` | Enum/String | **Obligatorio manejar estados completos (no booleanos):**<br>`PENDIENTE_ACTIVACION`, `ACTIVA`, `SUSPENDIDA_TEMPORAL`, `DESACTIVADA_PERMANENTE`. |

---

## 3. Estructuras de los Tokens (JWT)

El servicio manejará 2 tipos de tokens (ambos firmados con el mismo *Secret Key* inyectado por `.env`):

### 3.1 Token de Acceso (Access JWT)
Devuelto en `/auth/login`. Tiempo de expiración recomendado: **1 hora**.
```json
{
  "sub": "UUID-del-empleado", 
  "role": "USER",             
  "iat": 1712345678,          
  "exp": 1712349278           
}
```

### 3.2 Token de Activación / Recuperación (Reset Token - Stateless)
Generado al crear un empleado o pedir recuperación. Tiempo de expiración: **15 a 60 minutos**.
```json
{
  "sub": "UUID-del-empleado",
  "type": "RESET_PASSWORD",   // <-- Claim especial obligatorio
  "iat": 1712345678,
  "exp": 1712346578
}
```

---

## 4. Contratos de API REST (Endpoints)

### 4.1 Login (`POST /auth/login`)
*   **Descripción:** Recibe credenciales, verifica validez y retorna el Access JWT.
*   **Body:** `{"email": "juan@empresa.com", "password": "miPassword123"}`
*   **Response (200):** `{"token": "eyJhbG..."}`
*   **Errores:** `401 Unauthorized` (malas credenciales), `403 Forbidden` (cuenta suspendida/desactivada).

### 4.2 Solicitar Recuperación (`POST /auth/recover-password`)
*   **Descripción:** Genera un Reset Token y **publica el evento** `usuario.recuperacion`.
*   **Body:** `{"email": "juan@empresa.com"}`
*   **Response (200):** `{"message": "Instrucciones enviadas"}`

### 4.3 Restablecer Contraseña (`POST /auth/reset-password`)
*   **Descripción:** Recibe el Reset Token y la nueva contraseña. Hace el update en BD y **publica el evento** `cuenta.activada` (si es la primera vez).
*   **Body:** `{"resetToken": "eyJhb...", "newPassword": "nuevaPassword123"}`
*   **Response (200):** `{"message": "Contraseña actualizada"}`

### 4.4 Cambiar Contraseña (`POST /auth/change-password`)
*   **Descripción:** Un usuario logueado cambia su propia clave. Requiere JWT válido (validado por Gateway).
*   **Body:** `{"oldPassword": "vieja", "newPassword": "nueva"}`
*   **Response (200):** `{"message": "Contraseña cambiada exitosamente"}`

---

## 5. Matriz de Integración por Eventos (Para el Integrante 3)

El `auth-service` actuará como **Productor** y **Consumidor** en RabbitMQ.

### 5.1 Eventos que CONSUME (Acciones Internas):
| Evento | Acción en la Base de Datos (`auth-service`) | Efecto Secundario |
| :--- | :--- | :--- |
| `empleado.creado` | Crea cuenta en estado `PENDIENTE_ACTIVACION`, sin password, rol `USER`. | Genera Reset Token y **publica** `usuario.creado`. |
| `empleado.retirado` | Cambia estado a `DESACTIVADA_PERMANENTE`. | **Publica** `cuenta.desactivada` (permanente: true). |
| `vacaciones.iniciadas` | Cambia estado a `SUSPENDIDA_TEMPORAL`. | **Publica** `cuenta.desactivada` (motivo: VACACIONES, permanente: false). |
| `vacaciones.finalizadas` | **(CASO BORDE)** Si la cuenta está `DESACTIVADA_PERMANENTE`, **IGNORAR**. Si está `SUSPENDIDA_TEMPORAL`, pasar a `ACTIVA`. | **Publica** `cuenta.activada`. |

### 5.2 Eventos que PUBLICA (Hacia `notificaciones-service`):
*   `usuario.creado`: Se emite tras procesar `empleado.creado`. Lleva el Reset Token en el payload.
*   `usuario.recuperacion`: Se emite al llamar a `/auth/recover-password`. Lleva el Reset Token en el payload.
*   `cuenta.activada`: Se emite tras establecer la contraseña o volver de vacaciones.
*   `cuenta.desactivada`: Se emite al iniciar vacaciones o al retirar al empleado.
