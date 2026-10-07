using Microsoft.EntityFrameworkCore;
using Npgsql;
using EmpleadoService.Domain.Entities;
using EmpleadoService.Domain.Exceptions;
using EmpleadoService.Infrastructure.Persistence;

namespace EmpleadoService.Domain.Repositories;

public sealed class EmpleadoRepository : IEmpleadoRepository
{
    private readonly EmpleadoDbContext _dbContext;

    public EmpleadoRepository(EmpleadoDbContext dbContext)
    {
        _dbContext = dbContext;
    }

    public Task<Empleado?> ObtenerPorIdAsync(
        string id,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(id);
        return _dbContext.Empleados.AsNoTracking().SingleOrDefaultAsync(
            e => e.Id == id.Trim(), cancellationToken);
    }

    public async Task RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);

        if (_dbContext.Empleados.Local.Any(e => e.Id == empleado.Id)
            || await _dbContext.Empleados.AnyAsync(e => e.Id == empleado.Id, cancellationToken))
        {
            throw new EmpleadoDuplicadoException("id", empleado.Id);
        }

        if (_dbContext.Empleados.Local.Any(e => e.Email == empleado.Email)
            || await _dbContext.Empleados.AnyAsync(e => e.Email == empleado.Email, cancellationToken))
        {
            throw new EmpleadoDuplicadoException("email", empleado.Email);
        }

        if (_dbContext.Empleados.Local.Any(e => e.NumeroEmpleado == empleado.NumeroEmpleado)
            || await _dbContext.Empleados.AnyAsync(
                e => e.NumeroEmpleado == empleado.NumeroEmpleado,
                cancellationToken))
        {
            throw new EmpleadoDuplicadoException(
                "numeroEmpleado",
                empleado.NumeroEmpleado);
        }

        try
        {
            _dbContext.Empleados.Add(empleado);
            await _dbContext.SaveChangesAsync(cancellationToken);
            _dbContext.ChangeTracker.Clear();
        }
        catch (DbUpdateException exception) when (exception.InnerException is PostgresException
            { SqlState: PostgresErrorCodes.UniqueViolation } postgresException)
        {
            if (postgresException.ConstraintName == "PK_Empleados")
            {
                throw new EmpleadoDuplicadoException("id", empleado.Id);
            }

            if (postgresException.ConstraintName == "IX_Empleados_Email")
            {
                throw new EmpleadoDuplicadoException("email", empleado.Email);
            }

            if (postgresException.ConstraintName == "IX_Empleados_NumeroEmpleado")
            {
                throw new EmpleadoDuplicadoException(
                    "numeroEmpleado",
                    empleado.NumeroEmpleado);
            }

            throw;
        }
    }

    // Implementaci�n para guardar en DB los cambios de la baja l�gica y PUT (Reto 4)

    public async Task ActualizarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);
        _dbContext.Empleados.Update(empleado);
        await _dbContext.SaveChangesAsync(cancellationToken);
        _dbContext.ChangeTracker.Clear();
    }

    // Implementaci�n del filtro de auditor�a que exige el Reto 4

    public async Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(
        DateTime? desde,
        DateTime? hasta,
        CancellationToken cancellationToken = default)
    {
        var query = _dbContext.Empleados
            .AsNoTracking()
            .Where(e => e.Estado == EmpleadoService.Domain.Enums.EstadoEmpleado.Retirado);

        if (desde.HasValue)
        {
            query = query.Where(e => e.FechaRetiro >= desde.Value);
        }

        if (hasta.HasValue)
        {
            query = query.Where(e => e.FechaRetiro <= hasta.Value);
        }

        return await query.ToListAsync(cancellationToken);
    }
}
