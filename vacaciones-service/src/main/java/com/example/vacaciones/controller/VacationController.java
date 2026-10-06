package com.example.vacaciones.controller;

import java.net.URI;
import java.util.List;

import com.example.vacaciones.dto.ApiErrorResponse;
import com.example.vacaciones.dto.VacationRequest;
import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.service.VacationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vacaciones")
@Tag(name = "Vacaciones", description = "Gestión de períodos de vacaciones")
public class VacationController {

    private final VacationService service;

    public VacationController(VacationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Programar vacaciones",
            description = "Crea un período inclusivo. No admite fechas pasadas, empleados inexistentes o "
                    + "retirados, ni períodos que se solapen con vacaciones activas.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Período creado", content = @Content(
                    schema = @Schema(implementation = VacationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida: `validacion`, "
                    + "`fecha_en_el_pasado`, `empleado_inexistente`, `empleado_retirado` o `solapamiento`",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "El API Gateway no recibió un bearer JWT válido"),
            @ApiResponse(responseCode = "403", description = "El API Gateway requiere el rol ADMIN"),
            @ApiResponse(responseCode = "500", description = "Error interno del servicio")
    })
    public ResponseEntity<VacationResponse> create(@Valid @RequestBody VacationRequest request) {
        VacationResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/vacaciones/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar vacaciones por identificador",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Período encontrado",
                    content = @Content(schema = @Schema(implementation = VacationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe el período (`no_encontrada`)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "El API Gateway no recibió un bearer JWT válido"),
            @ApiResponse(responseCode = "403", description = "El API Gateway denegó el acceso"),
            @ApiResponse(responseCode = "500", description = "Error interno del servicio")
    })
    public VacationResponse getById(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    @Operation(summary = "Listar períodos de vacaciones",
            description = "Sin `empleadoId`, devuelve todos los períodos; con el parámetro, filtra por empleado.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Períodos encontrados",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = VacationResponse.class)))),
            @ApiResponse(responseCode = "401", description = "El API Gateway no recibió un bearer JWT válido"),
            @ApiResponse(responseCode = "403", description = "El API Gateway denegó el acceso"),
            @ApiResponse(responseCode = "500", description = "Error interno del servicio")
    })
    public List<VacationResponse> list(
            @Parameter(description = "Filtra por identificador de empleado", in = ParameterIn.QUERY,
                    example = "E001")
            @RequestParam(required = false) String empleadoId) {
        return (empleadoId == null) ? service.list(null) : service.list(empleadoId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar un período programado",
            description = "Solo permite cancelar períodos en estado `PROGRAMADA`.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Período cancelado",
                    content = @Content(schema = @Schema(implementation = VacationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe el período (`no_encontrada`)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado incompatible (`estado_invalido`)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "El API Gateway no recibió un bearer JWT válido"),
            @ApiResponse(responseCode = "403", description = "El API Gateway requiere el rol ADMIN"),
            @ApiResponse(responseCode = "500", description = "Error interno del servicio")
    })
    public ResponseEntity<VacationResponse> cancel(@PathVariable String id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
