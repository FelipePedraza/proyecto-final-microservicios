using EmpleadoService.Infrastructure.Messaging;
using EmpleadoService.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace EmpleadoService.Infrastructure.Health;

public sealed record ServiceHealthSnapshot(bool Database, bool MessageBroker);

public interface IServiceHealthProbe
{
    Task<ServiceHealthSnapshot> CheckAsync(CancellationToken cancellationToken = default);
}

public sealed class ServiceHealthProbe(
    EmpleadoDbContext database,
    IEventPublisher publisher) : IServiceHealthProbe
{
    public async Task<ServiceHealthSnapshot> CheckAsync(CancellationToken cancellationToken = default)
    {
        bool databaseUp;
        try
        {
            databaseUp = await database.Database.CanConnectAsync(cancellationToken);
        }
        catch
        {
            databaseUp = false;
        }

        return new ServiceHealthSnapshot(databaseUp, publisher.IsHealthy);
    }
}
