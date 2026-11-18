namespace EmpleadoService.Infrastructure.Configuration;

public static class SecretReader
{
    public static string ReadRequired(
        string secretName,
        string environmentVariable,
        string? secretsDirectory = null)
    {
        secretsDirectory ??= Environment.GetEnvironmentVariable("SECRETS_DIR")
            ?? "/run/secrets";
        var secretPath = Path.Combine(secretsDirectory, secretName);

        string? value;
        if (File.Exists(secretPath))
        {
            try
            {
                value = File.ReadAllText(secretPath).Trim();
            }
            catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
            {
                throw new InvalidOperationException(
                    $"ERROR: No se pudo leer el secreto '{secretName}' desde {secretPath}.",
                    exception);
            }
        }
        else
        {
            value = Environment.GetEnvironmentVariable(environmentVariable)?.Trim();
        }

        var hint = $" Montar /run/secrets/{secretName} o definir {environmentVariable}.";
        if (value is null)
        {
            throw new InvalidOperationException(
                $"ERROR: Secreto '{secretName}' no configurado.{hint}");
        }
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new InvalidOperationException(
                $"ERROR: Secreto '{secretName}' está vacío.{hint}");
        }
        if (value.StartsWith("REEMPLAZAR", StringComparison.Ordinal)
            || value.StartsWith("GESTIONADO_EN_SECRETS", StringComparison.Ordinal))
        {
            throw new InvalidOperationException(
                $"ERROR: Secreto '{secretName}' contiene un marcador de plantilla inseguro.{hint}");
        }

        return value;
    }
}
