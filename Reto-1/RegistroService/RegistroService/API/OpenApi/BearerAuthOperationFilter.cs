using Microsoft.AspNetCore.Mvc.ApiExplorer;
using Microsoft.OpenApi;
using Swashbuckle.AspNetCore.SwaggerGen;

namespace RegistroService.API.OpenApi;

/// <summary>
/// Marks an operation as requiring the gateway's Bearer JWT token in generated API documentation.
/// This is documentation metadata only; RegistroService does not authenticate tokens itself.
/// </summary>
public sealed class BearerAuthRequiredMetadata;

/// <summary>Adds the Bearer scheme to operations explicitly marked for gateway authentication.</summary>
public sealed class BearerAuthOperationFilter : IOperationFilter
{
    public void Apply(OpenApiOperation operation, OperationFilterContext context)
    {
        if (!context.ApiDescription.ActionDescriptor.EndpointMetadata
                .OfType<BearerAuthRequiredMetadata>()
                .Any())
        {
            return;
        }

        operation.Security ??= [];
        operation.Security.Add(new OpenApiSecurityRequirement
        {
            [new OpenApiSecuritySchemeReference("Bearer", context.Document)] = []
        });
    }
}
