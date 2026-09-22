namespace RegistroService.Infrastructure.Messaging;

public class EventEnvelope<T>
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Type { get; set; } = string.Empty;
    public string Version { get; set; } = "1.0";
    public string OccurredAt { get; set; } = DateTime.UtcNow.ToString("O");
    public string Producer { get; set; } = "empleados-service";
    public T Data { get; set; } = default!;
}