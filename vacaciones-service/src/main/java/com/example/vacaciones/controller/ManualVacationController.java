package com.example.vacaciones.controller;

import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.service.VacationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vacaciones")
@ConditionalOnProperty(prefix = "vacaciones.scheduler", name = "manual-enabled", havingValue = "true")
public class ManualVacationController {

    private final VacationService service;

    public ManualVacationController(VacationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/forzar-inicio")
    public VacationResponse forceStart(@PathVariable String id) {
        return service.forceStart(id);
    }

    @PostMapping("/{id}/forzar-fin")
    public VacationResponse forceFinish(@PathVariable String id) {
        return service.forceFinish(id);
    }
}
