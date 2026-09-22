# Reto 4 - Integrante 1 (Message Broker y Productor)

A continuación se detalla la implementación realizada para la base de eventos y la refactorización del servicio de empleados. Todos los cambios necesarios para que los Integrantes 2, 3 y 4 comiencen su desarrollo están listos.

### ✅ 1. Configuración del Broker (Infraestructura)
- Agregamos **RabbitMQ** al docker-compose.yml (puertos 5672 y 15672).
- Redactamos la justificación técnica de RabbitMQ frente a Kafka en el README.md principal del proyecto.

### ✅ 2. Baja Lógica y Auditoría (Servicio de Empleados)
- Evitamos el borrado físico de registros de empleados.
- Se agregó el campo FechaRetiro y el estado RETIRADO en el Dominio.
- Se implementó el endpoint DELETE /empleados/{id} para ejecutar la baja lógica.
- Se implementó el endpoint de auditoría GET /empleados?estado=RETIRADO&desde=...&hasta=....
- Ejecutamos las migraciones de Entity Framework Core para que la base de datos (Postgres) soporte la nueva columna sin perder datos.

### ✅ 3. Publicación de Eventos (Productor)
- Creamos la clase RabbitMqPublisher que inyecta los mensajes hacia el empleados_exchange usando el patrón de enrutamiento **Fan-out**.
- Respetamos estrictamente el formato *Envelope* exigido por el catálogo de eventos.
- Se configuró el disparo automático de eventos:
  - empleado.creado al crear (POST).
  - empleado.actualizado al editar (se agregó soporte para el endpoint PUT).
  - empleado.retirado al eliminar (baja lógica, DELETE).
- **Resiliencia**: Manejamos los errores de red de modo que si RabbitMQ cae, el servicio solo guarda un log y la transacción en la base de datos no sufre rollback.
