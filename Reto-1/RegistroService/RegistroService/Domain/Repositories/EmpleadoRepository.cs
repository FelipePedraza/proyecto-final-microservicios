using Microsoft.EntityFrameworkCore;
using Npgsql;
using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Infrastructure.Persistence;

namespace RegistroService.Domain.Repositories;

public sealed class EmpleadoRepository : IEmpleadoRepository
{
    private readonly RegistroDbContext dbContext;

    public EmpleadoRepository(RegistroDbContext dbContext)
    {
        this.dbContext = dbContext;
    }

    public Task<Empleado?> ObtenerPorIdAsync(
        string id,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(id);
        return dbContext.Empleados.AsNoTracking().SingleOrDefaultAsync(
            e => e.Id == id.Trim(), cancellationToken);
    }

    public async Task RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);

        if (dbContext.Empleados.Local.Any(e => e.Id == empleado.Id)
            || await dbContext.Empleados.AnyAsync(e => e.Id == empleado.Id, cancellationToken))
        {
            throw new EmpleadoDuplicadoException("id", empleado.Id);
        }

        if (dbContext.Empleados.Local.Any(e => e.Email == empleado.Email)
            || await dbContext.Empleados.AnyAsync(e => e.Email == empleado.Email, cancellationToken))
        {
            throw new EmpleadoDuplicadoException("email", empleado.Email);
        }

        if (dbContext.Empleados.Local.Any(e => e.NumeroEmpleado == empleado.NumeroEmpleado)
            || await dbContext.Empleados.AnyAsync(
                e => e.NumeroEmpleado == empleado.NumeroEmpleado,
                cancellationToken))
        {
            throw new EmpleadoDuplicadoException(
                "numeroEmpleado",
                empleado.NumeroEmpleado);
        }

        try
        {
            dbContext.Empleados.Add(empleado);
            await dbContext.SaveChangesAsync(cancellationToken);
            dbContext.ChangeTracker.Clear();
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

    // Implementación para guardar en DB los cambios de la baja lógica y PUT (Reto 4)

    public async Task ActualizarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);
        dbContext.Empleados.Update(empleado);
        await dbContext.SaveChangesAsync(cancellationToken);
        dbContext.ChangeTracker.Clear();
    }

    // Implementación del filtro de auditoría que exige el Reto 4

    public async Task<IEnumerable<Empleado>> ObtenerRetiradosAsync(
        DateTime? desde,
        DateTime? hasta,
        CancellationToken cancellationToken = default)
    {
        var query = dbContext.Empleados
            .AsNoTracking()
            .Where(e => e.Estado == RegistroService.Domain.Enums.EstadoEmpleado.Retirado);

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
