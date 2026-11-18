using EmpleadoService.Infrastructure.Configuration;
using Xunit;

namespace EmpleadoService.Tests;

public sealed class SecretReaderTests
{
    [Fact]
    public void ReadRequired_PrefiereArchivoMontado()
    {
        var directory = Directory.CreateTempSubdirectory("empleado-secrets-");
        try
        {
            File.WriteAllText(Path.Combine(directory.FullName, "test_secret"), "valor-seguro\n");

            var value = SecretReader.ReadRequired(
                "test_secret",
                "TEST_SECRET_FALLBACK_UNUSED",
                directory.FullName);

            Assert.Equal("valor-seguro", value);
        }
        finally
        {
            directory.Delete(recursive: true);
        }
    }

    [Fact]
    public void ReadRequired_FallaSiNoExiste()
    {
        var directory = Directory.CreateTempSubdirectory("empleado-secrets-");
        try
        {
            var exception = Assert.Throws<InvalidOperationException>(() =>
                SecretReader.ReadRequired(
                    "test_secret",
                    "TEST_SECRET_FALLBACK_UNUSED",
                    directory.FullName));

            Assert.Equal(
                "ERROR: Secreto 'test_secret' no configurado. " +
                "Montar /run/secrets/test_secret o definir TEST_SECRET_FALLBACK_UNUSED.",
                exception.Message);
        }
        finally
        {
            directory.Delete(recursive: true);
        }
    }

    [Fact]
    public void ReadRequired_UsaVariableSiElArchivoNoExiste()
    {
        var directory = Directory.CreateTempSubdirectory("empleado-secrets-");
        const string variable = "TEST_SECRET_FALLBACK";
        Environment.SetEnvironmentVariable(variable, "fallback-seguro");
        try
        {
            var value = SecretReader.ReadRequired("test_secret", variable, directory.FullName);

            Assert.Equal("fallback-seguro", value);
        }
        finally
        {
            Environment.SetEnvironmentVariable(variable, null);
            directory.Delete(recursive: true);
        }
    }

    [Fact]
    public void ReadRequired_RechazaMarcadorDePlantilla()
    {
        var directory = Directory.CreateTempSubdirectory("empleado-secrets-");
        try
        {
            File.WriteAllText(Path.Combine(directory.FullName, "test_secret"), "REEMPLAZAR_CON_VALOR_REAL");

            var exception = Assert.Throws<InvalidOperationException>(() =>
                SecretReader.ReadRequired("test_secret", "TEST_SECRET_FALLBACK_UNUSED", directory.FullName));

            Assert.Contains("ERROR: Secreto 'test_secret' contiene un marcador de plantilla inseguro.",
                exception.Message);
        }
        finally
        {
            directory.Delete(recursive: true);
        }
    }

    [Fact]
    public void ReadRequired_DistingueArchivoVacio()
    {
        var directory = Directory.CreateTempSubdirectory("empleado-secrets-");
        try
        {
            File.WriteAllText(Path.Combine(directory.FullName, "test_secret"), "  \n");

            var exception = Assert.Throws<InvalidOperationException>(() =>
                SecretReader.ReadRequired("test_secret", "TEST_SECRET_FALLBACK_UNUSED", directory.FullName));

            Assert.Contains("ERROR: Secreto 'test_secret' está vacío.", exception.Message);
        }
        finally
        {
            directory.Delete(recursive: true);
        }
    }
}
