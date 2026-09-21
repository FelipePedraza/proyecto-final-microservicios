using Microsoft.EntityFrameworkCore;
using Npgsql;
using RegistroService.Domain.Entities;
using RegistroService.Domain.Exceptions;
using RegistroService.Infrastructure.Persistence;

namespace RegistroService.Domain.Repositories;

public sealed class EmpleadoRepository : IEmpleadoRepository
{
    private readonly RegistroDbContext dbContext;
    private readonly SemaphoreSlim sync = new(1, 1);

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

    public Task<bool> ExisteEmailAsync(
        string email,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(email);
        return dbContext.Empleados.AnyAsync(e => e.Email == email.Trim(), cancellationToken);
    }

    public Task<bool> ExisteNumeroEmpleadoAsync(
        string numeroEmpleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(numeroEmpleado);
        return dbContext.Empleados.AnyAsync(
            e => e.NumeroEmpleado == numeroEmpleado.Trim(), cancellationToken);
    }

    public async Task RegistrarAsync(
        Empleado empleado,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(empleado);

        await sync.WaitAsync(cancellationToken);
        try
        {
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
        finally
        {
            sync.Release();
        }
    }
}
