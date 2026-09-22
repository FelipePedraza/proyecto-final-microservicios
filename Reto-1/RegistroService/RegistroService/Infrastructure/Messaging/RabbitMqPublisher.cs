using System.Text;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using RabbitMQ.Client;

namespace RegistroService.Infrastructure.Messaging;

public interface IEventPublisher
{
    void Publish<T>(string eventType, T data);
}

public class RabbitMqPublisher : IEventPublisher, IDisposable
{
    private readonly IConnection? _connection;
    private readonly IModel? _channel;
    private readonly ILogger<RabbitMqPublisher> _logger;
    private const string ExchangeName = "empleados_exchange";

    public RabbitMqPublisher(IConfiguration configuration, ILogger<RabbitMqPublisher> logger)
    {
        _logger = logger;
        try
        {
            var factory = new ConnectionFactory
            {
                HostName = configuration["RabbitMQ:Host"] ?? "localhost",
                Port = int.Parse(configuration["RabbitMQ:Port"] ?? "5672"),
                UserName = configuration["RabbitMQ:Username"] ?? "admin",
                Password = configuration["RabbitMQ:Password"] ?? "admin"
            };
            
            _connection = factory.CreateConnection();
            _channel = _connection.CreateModel();
            
            // Declaramos un exchange tipo fanout
            _channel.ExchangeDeclare(exchange: ExchangeName, type: ExchangeType.Fanout, durable: true);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error al conectar con RabbitMQ. Los eventos no se publicar�n.");
        }
    }

    public void Publish<T>(string eventType, T data)
    {
        if (_channel == null || _connection == null || !_connection.IsOpen)
        {
            _logger.LogWarning("No hay conexi�n con RabbitMQ. El evento {EventType} no se publicar�, pero la operaci�n contin�a.", eventType);
            return;
        }

        try
        {
            var envelope = new EventEnvelope<T>
            {
                Type = eventType,
                Data = data
            };

            var body = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(envelope));

            _channel.BasicPublish(
                exchange: ExchangeName,
                routingKey: "", // En fanout, el routingKey se ignora
                basicProperties: null,
                body: body);

            _logger.LogInformation("Evento {EventType} publicado exitosamente en {ExchangeName}", eventType, ExchangeName);
        }
        catch (Exception ex)
        {
            // Seg�n los requerimientos: Si falla la publicaci�n, registramos el error pero NO revertimos la BD
            _logger.LogError(ex, "Error al publicar el evento {EventType}", eventType);
        }
    }

    public void Dispose()
    {
        _channel?.Dispose();
        _connection?.Dispose();
    }
}