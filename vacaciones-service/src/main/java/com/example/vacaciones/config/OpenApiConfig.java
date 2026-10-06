package com.example.vacaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vacacionesOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Access JWT emitido por POST /auth/login. "
                        + "La autenticación y autorización se aplican en el API Gateway.");

        return new OpenAPI()
                .info(new Info()
                        .title("Vacaciones Service API")
                        .version("1.0.0")
                        .description("""
                                API para crear, consultar y cancelar períodos de vacaciones, y ejecutar
                                transiciones manuales de ciclo de vida cuando están habilitadas.

                                Al acceder a través del API Gateway, los endpoints de negocio requieren
                                un bearer JWT; las operaciones de escritura requieren el rol ADMIN.
                                Los endpoints de salud y esta documentación son públicos. Los endpoints
                                manuales solo se registran cuando `VACACIONES_MANUAL_ENABLED=true`.

                                Errores de negocio: `validacion` (incluye cuerpos inválidos),
                                `fecha_en_el_pasado`, `empleado_inexistente`, `empleado_retirado`,
                                `solapamiento`, `no_encontrada` y `estado_invalido`.
                                """))
                .components(new Components().addSecuritySchemes("BearerAuth", bearerScheme));
    }
}
