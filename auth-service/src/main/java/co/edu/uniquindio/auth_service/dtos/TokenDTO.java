package co.edu.uniquindio.auth_service.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token JWT de acceso.")
public record TokenDTO(
        @Schema(description = "JWT de acceso. Validez de una hora.", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
        String token) {
}
