# language: es
Característica: Offboarding y desvinculación de empleados
  Como sistema de recursos humanos
  Quiero desvincular a un empleado
  Para asegurar que sus accesos sean revocados permanentemente y mantener auditoría

  Antecedentes:
    Dado que estoy autenticado como "ADMIN"
    Y que existe un empleado activo con credenciales establecidas

  Escenario: Desvinculación completa de un empleado
    Cuando registro la desvinculación del empleado
    Entonces la respuesta debe tener código 204
    Y eventualmente el empleado no puede iniciar sesión
    Y la recuperación de contraseña falla para el empleado retirado

  Escenario: Consulta de auditoría de empleados retirados
    Cuando registro la desvinculación del empleado
    Y consulto la lista de empleados con estado "RETIRADO"
    Entonces el empleado retirado aparece en la respuesta
