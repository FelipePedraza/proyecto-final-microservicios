# language: es
@seguridad
Característica: Seguridad y control de acceso
  Como sistema de autenticación
  Quiero controlar el acceso a los recursos protegidos
  Para garantizar que solo los usuarios autorizados realicen operaciones

  Antecedentes:
    Dado que el sistema está desplegado y accesible a través del gateway

  Escenario: Acceso denegado sin token de autenticación
    Cuando consulto la lista de empleados sin token de autenticación
    Entonces la respuesta debe tener código 401

  Escenario: Acceso denegado con un token malformado
    Dado que uso un token de autenticación malformado
    Cuando consulto la lista de empleados
    Entonces la respuesta debe tener código 401

  Escenario: Un usuario puede consultar recursos protegidos
    Dado que estoy autenticado como "USER"
    Cuando consulto la lista de empleados
    Entonces la respuesta debe tener código 200

  Escenario: Un usuario no puede crear empleados
    Dado que estoy autenticado como "USER"
    Cuando intento registrar un empleado con datos válidos
    Entonces la respuesta debe tener código 403

  Escenario: Un usuario no puede eliminar empleados
    Dado que existe un empleado de prueba para verificar el control de acceso
    Y que estoy autenticado como "USER"
    Cuando intento eliminar el empleado de prueba
    Entonces la respuesta debe tener código 403

  Escenario: Un administrador puede crear y eliminar empleados
    Dado que estoy autenticado como "ADMIN"
    Cuando registro un empleado con datos válidos
    Entonces la respuesta debe tener código 201
    Cuando elimino el empleado registrado
    Entonces la respuesta debe tener código 204
