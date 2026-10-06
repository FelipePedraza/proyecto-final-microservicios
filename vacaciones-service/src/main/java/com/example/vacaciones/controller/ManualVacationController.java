package com.example.vacaciones.controller;

import com.example.vacaciones.dto.ApiErrorResponse;
import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.service.VacationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vacaciones")
@ConditionalOnProperty(prefix = "vacaciones.scheduler", name = "manual-enabled", havingValue = "true")
@Tag(name = "Ciclo de vida manual", description = "Transiciones habilitadas solo para desarrollo o demostraciones")
public class ManualVacationController {

    private final VacationService service;

    public ManualVacationController(VacationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/forzar-inicio")
    @Operation(summary = "Forzar el inicio de vacaciones",
            description = "Cambia un período `PROGRAMADA` a `EN_CURSO` y publica su evento de inicio. "
                    + "Disponible únicamente con `VACACIONES_MANUAL_ENABLED=true`.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Período iniciado",
                    content = @Content(schema = @Schema(implementation = VacationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe el período (`no_encontrada`)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado incompatible (`estado_invalido`)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "El API Gateway no recibió un bearer JWT válido"),
            @ApiResponse(responseCode = "403", description = "El API Gateway requiere el rol ADMIN"),
            @ApiResponse(responseCode = "500", description = "Error interno del servicio")
    })
    public VacationResponse forceStart(@PathVariable String id) {
        return service.forceStart(id);
    }

    @PostMapping("/{id}/forzar-fin")
    @Operation(summary = "Forzar el fin de vacaciones",
            description = "Cambia un período `EN_CURSO` a `FINALIZADA` y publica su evento de fin. "
                    + "Disponible únicamente con `VACACIONES_MANUAL_ENABLED=true`.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Período finalizado",
                    content = @Content(schema = @Schema(implementation = VacationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe el período (`no_encontrada`)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado incompatible (`estado_invalido`)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "El API Gateway no recibió un bearer JWT válido"),
            @ApiResponse(responseCode = "403", description = "El API Gateway requiere el rol ADMIN"),
            @ApiResponse(responseCode = "500", description = "Error interno del servicio")
    })
    public VacationResponse forceFinish(@PathVariable String id) {
        return service.forceFinish(id);
    }
}
