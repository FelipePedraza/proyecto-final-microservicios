# Evidencias del Reto 3

Esta carpeta contiene las capturas obtenidas durante la demostración real del API Gateway y el Circuit Breaker.

## Lista de verificación

- [ ] `01-compose-healthy.png`
- [ ] `02-acceso-directo-rechazado.png`
- [ ] `03-recurso-por-gateway.png`
- [ ] `04-gateway-503-json.png`
- [ ] `05-circuito-closed.png`
- [ ] `06-salto-tiempos-open.png`
- [ ] `07-circuito-open.png`
- [ ] `08-transiciones-logs.png`
- [ ] `09-recuperacion-400.png`
- [ ] `10-circuito-closed-final.png`

## Cómo insertar una captura

Después de guardar la imagen en esta carpeta, puede mostrarse en este documento así:

```markdown
![Descripción verificable de la evidencia](01-compose-healthy.png)
```

Debajo de cada imagen se recomienda registrar:

- fecha y hora de ejecución;
- comando utilizado;
- resultado observado;
- criterio del Reto 3 que demuestra;
- cualquier desviación respecto al resultado esperado.

## Resultados

### 1. Compose saludable y único puerto público

![docker_compose_check.png](capturas/docker_compose_check.png)

### 2. Acceso directo rechazado

![port_check.png](capturas/port_check.png)

### 3. Recurso funcionando por el Gateway

![departamentos_200.png](capturas/departamentos_200.png)

### 4. Respuesta 503 controlada

![control_http_503.png](capturas/control_http_503.png)

### 5. Estado inicial CLOSED

![status_closed.png](capturas/status_closed.png)

### 6. Salto en tiempos de respuesta

![saltos_tiempo_respuesta.png](capturas/saltos_tiempo_respuesta.png)

### 7. Estado OPEN y fallback

![status_half_open.png](capturas/status_half_open.png)

### 8. Transiciones del Circuit Breaker

![transiciones_circuit.png](capturas/transiciones_circuit.png)

### 9. Recuperación automática

![departamentos_400.png](capturas/departamentos_400.png)

### 10. Estado final CLOSED

![status_closed_2.png](capturas/status_closed_2.png)