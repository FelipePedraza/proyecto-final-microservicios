# language: es
@vacaciones
Característica: Gestión de vacaciones y suspensión de cuentas
  Como sistema de recursos humanos
  Quiero gestionar los períodos de vacaciones de los empleados
  Para suspender temporalmente sus cuentas durante su descanso y evitar solapamientos

  Antecedentes:
    Dado que estoy autenticado como "ADMIN"
    Y que existe un empleado activo con credenciales establecidas

  Escenario: Programación exitosa de vacaciones
    Cuando programo un período de vacaciones desde dentro de 7 días hasta dentro de 14 días
    Entonces la respuesta debe tener código 201

  Escenario: Ciclo de suspensión y reactivación de la cuenta
    Cuando programo un período de vacaciones desde dentro de 7 días hasta dentro de 14 días
    Entonces la respuesta debe tener código 201
    Cuando fuerzo el inicio del período de vacaciones programado
    Entonces la respuesta debe tener código 200
    Y eventualmente el empleado no puede iniciar sesión
    Cuando fuerzo el fin del período de vacaciones en curso
    Entonces la respuesta debe tener código 200
    Y eventualmente el empleado puede iniciar sesión nuevamente

  Escenario: Rechazo por fechas incoherentes
    Cuando programo un período de vacaciones desde dentro de 7 días hasta dentro de 2 días
    Entonces la respuesta debe tener código 400

  Escenario: Rechazo por solapamiento con un período existente
    Cuando programo un período de vacaciones desde dentro de 7 días hasta dentro de 14 días
    Y programo un período de vacaciones desde dentro de 10 días hasta dentro de 20 días
    Entonces la respuesta debe tener código 400
