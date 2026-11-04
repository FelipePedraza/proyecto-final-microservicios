using System.Diagnostics;
using System.Text;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using OpenTelemetry;
using OpenTelemetry.Context.Propagation;
using OpenTelemetry.Trace;
using RabbitMQ.Client;

namespace EmpleadoService.Infrastructure.Messaging;

public interface IEventPublisher
{
    bool IsHealthy => true;
    void Publish<T>(string eventType, T data);
}

public class RabbitMqPublisher : IEventPublisher, IDisposable
{
    private readonly IConnection? _connection;
    private readonly IModel? _channel;
    private readonly ILogger<RabbitMqPublisher> _logger;
    private const string ExchangeName = "empleados_exchange";
    private static readonly ActivitySource ActivitySource = new("EmpleadoService.Messaging");

    public bool IsHealthy => _connection?.IsOpen == true && _channel?.IsOpen == true;

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

        Activity? publishActivity = null;
        try
        {
            var envelope = new EventEnvelope<T>
            {
                Type = eventType,
                Data = data
            };

            var body = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(envelope));

            publishActivity = ActivitySource.StartActivity(
                $"{ExchangeName} publish",
                ActivityKind.Producer);
            publishActivity?.SetTag("messaging.system", "rabbitmq");
            publishActivity?.SetTag("messaging.destination.name", ExchangeName);
            publishActivity?.SetTag("messaging.operation.name", "publish");
            publishActivity?.SetTag("messaging.message.type", eventType);

            var properties = _channel.CreateBasicProperties();
            properties.Persistent = true;
            properties.ContentType = "application/json";
            properties.ContentEncoding = "UTF-8";
            properties.Headers = new Dictionary<string, object>();

            var propagationContext = new PropagationContext(
                publishActivity?.Context ?? Activity.Current?.Context ?? default,
                Baggage.Current);
            Propagators.DefaultTextMapPropagator.Inject(
                propagationContext,
                properties,
                static (carrier, key, value) =>
                    carrier.Headers[key] = Encoding.UTF8.GetBytes(value));

            _channel.BasicPublish(
                exchange: ExchangeName,
                routingKey: "", // En fanout, el routingKey se ignora
                basicProperties: properties,
                body: body);

            _logger.LogInformation("Evento {EventType} publicado exitosamente en {ExchangeName}", eventType, ExchangeName);
        }
        catch (Exception ex)
        {
            publishActivity?.SetStatus(ActivityStatusCode.Error, ex.Message);
            publishActivity?.AddException(ex);
            // Seg�n los requerimientos: Si falla la publicaci�n, registramos el error pero NO revertimos la BD
            _logger.LogError(ex, "Error al publicar el evento {EventType}", eventType);
        }
        finally
        {
            publishActivity?.Dispose();
        }
    }

    public void Dispose()
    {
        _channel?.Dispose();
        _connection?.Dispose();
    }
}
