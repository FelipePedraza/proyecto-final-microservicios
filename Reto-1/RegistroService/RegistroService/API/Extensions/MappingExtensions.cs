using RegistroService.Domain.Entities;
using RegistroService.Domain.Enums;
using RegistroService.API.DTOs;

namespace RegistroService.API.Extensions;

/// <summary>
/// Extensiones para mapear entre entidades del dominio y DTOs de la API.
/// </summary>
public static class MappingExtensions
{
    /// <summary>
    /// Convierte un request de creación de empleado en una entidad Empleado del dominio.
    /// </summary>
    /// <param name="request">DTO con los datos de creación del empleado.</param>
    /// <returns>Entidad Empleado lista para ser procesada por el servicio.</returns>
    /// <remarks>
    /// - El ID lo proporciona el cliente como parte del modelo canónico
    /// - El estado se establece automáticamente como Activo
    /// </remarks>
    public static Empleado ToEntity(this CreateEmpleadoRequest request)
    {
        return new Empleado(
            id: request.Id,
            nombre: request.Nombre,
            apellido: request.Apellido,
            email: request.Email,
            numeroEmpleado: request.NumeroEmpleado,
            cargo: request.Cargo,
            area: request.Area,
            departamentoId: request.DepartamentoId,
            fechaIngreso: request.FechaIngreso
        );
    }

    /// <summary>
    /// Convierte una entidad Empleado en un DTO de respuesta para la API.
    /// </summary>
    /// <param name="empleado">Entidad Empleado a convertir.</param>
    /// <returns>DTO con la información del empleado para retornar en la respuesta.</returns>
    public static EmpleadoResponse ToResponse(this Empleado empleado)
    {
        return new EmpleadoResponse
        {
            Id = empleado.Id,
            Nombre = empleado.Nombre,
            Apellido = empleado.Apellido,
            Email = empleado.Email,
            NumeroEmpleado = empleado.NumeroEmpleado,
            Cargo = empleado.Cargo,
            Area = empleado.Area,
            DepartamentoId = empleado.DepartamentoId,
            FechaIngreso = empleado.FechaIngreso,
            Estado = empleado.Estado.ToCanonicalValue()
        };
    }

    private static string ToCanonicalValue(this EstadoEmpleado estado) => estado switch
    {
        EstadoEmpleado.Activo => "ACTIVO",
        EstadoEmpleado.EnVacaciones => "EN_VACACIONES",
        EstadoEmpleado.Retirado => "RETIRADO",
        EstadoEmpleado.PendienteValidacion => "PENDIENTE_VALIDACION",
        _ => throw new ArgumentOutOfRangeException(nameof(estado), estado, null)
    };
}
