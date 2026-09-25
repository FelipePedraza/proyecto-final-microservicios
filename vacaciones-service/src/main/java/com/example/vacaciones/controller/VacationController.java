package com.example.vacaciones.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vacaciones.dto.VacationRequest;
import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.service.VacationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/vacaciones")
public class VacationController {

    private final VacationService service;

    public VacationController(VacationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<VacationResponse> create(@Valid @RequestBody VacationRequest request) {
        VacationResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/vacaciones/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public VacationResponse getById(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping
    public List<VacationResponse> list(@RequestParam(required = false) String empleadoId) {
        return (empleadoId == null) ? service.list(null) : service.list(empleadoId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<VacationResponse> cancel(@PathVariable String id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
