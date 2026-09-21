using Microsoft.EntityFrameworkCore;
using RegistroService.Domain.Entities;

namespace RegistroService.Infrastructure.Persistence;

public sealed class RegistroDbContext(DbContextOptions<RegistroDbContext> options) : DbContext(options)
{
    public DbSet<Empleado> Empleados => Set<Empleado>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        var empleado = modelBuilder.Entity<Empleado>();
        empleado.ToTable("Empleados");
        empleado.HasKey(e => e.Id);
        empleado.Property(e => e.Id).HasMaxLength(100);
        empleado.Property(e => e.Nombre).HasMaxLength(150).IsRequired();
        empleado.Property(e => e.Apellido).HasMaxLength(150).IsRequired();
        empleado.Property(e => e.Email).HasMaxLength(254).IsRequired();
        empleado.Property(e => e.NumeroEmpleado).HasMaxLength(100).IsRequired();
        empleado.Property(e => e.Cargo).HasMaxLength(150).IsRequired();
        empleado.Property(e => e.Area).HasMaxLength(150).IsRequired();
        empleado.Property(e => e.DepartamentoId).HasMaxLength(100).IsRequired();
        empleado.Property(e => e.FechaIngreso).IsRequired();
        empleado.Property(e => e.Estado).HasConversion<string>().HasMaxLength(30).IsRequired();
        empleado.HasIndex(e => e.Email).IsUnique().HasDatabaseName("IX_Empleados_Email");
        empleado.HasIndex(e => e.NumeroEmpleado).IsUnique().HasDatabaseName("IX_Empleados_NumeroEmpleado");
    }
}
