# language: es
Característica: Gestión de vacaciones y suspensión de cuentas
  Como sistema de recursos humanos
  Quiero gestionar los períodos de vacaciones de los empleados
  Para suspender temporalmente sus cuentas durante su descanso y evitar solapamientos

  Antecedentes:
    Dado que estoy autenticado como "ADMIN"
    Y que existe un empleado activo con credenciales establecidas

  Escenario: Programación de vacaciones y ciclo de suspensión/reactivación
    Cuando programo un período de vacaciones que inicia de inmediato
    Entonces la respuesta debe tener código 201
    Y eventualmente el empleado no puede iniciar sesión
    Cuando el período de vacaciones finaliza
    Entonces eventualmente el empleado puede iniciar sesión nuevamente

  Escenario: Rechazo por fechas incoherentes
    Cuando programo un período de vacaciones con fecha de fin anterior a la de inicio
    Entonces la respuesta debe tener código 400

  Escenario: Rechazo por solapamiento con un período existente
    Cuando programo un período de vacaciones para la próxima semana
    Y programo otro período de vacaciones que se solapa con el anterior
    Entonces la respuesta debe tener código 400
