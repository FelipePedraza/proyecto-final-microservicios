package com.example.vacaciones.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.vacaciones.domain.Vacation;
import com.example.vacaciones.domain.VacationStatus;
import com.example.vacaciones.dto.VacationRequest;
import com.example.vacaciones.dto.VacationResponse;
import com.example.vacaciones.exception.VacationConflictException;
import com.example.vacaciones.exception.VacationNotFoundException;
import com.example.vacaciones.exception.VacationStateException;
import com.example.vacaciones.exception.VacationValidationException;
import com.example.vacaciones.messaging.EmployeeReplicaRepository;
import com.example.vacaciones.repository.VacationRepository;

@Service
public class VacationService {

    private final VacationRepository repository;
    private final EmployeeReplicaRepository employees;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;

    public VacationService(
            VacationRepository repository,
            EmployeeReplicaRepository employees,
            Clock clock,
            ApplicationEventPublisher publisher
    ) {
        this.repository = repository;
        this.employees = employees;
        this.clock = clock;
        this.publisher = publisher;
    }

    @Transactional
    public VacationResponse create(VacationRequest request) {
        LocalDate today = LocalDate.now(clock);

        if (request.fechaInicio().isBefore(today)) {
            throw new VacationValidationException("fecha_en_el_pasado", "La fecha de inicio no puede estar en el pasado");
        }

        var employee = employees.findById(request.empleadoId().trim())
                .orElseThrow(() -> new VacationValidationException(
                        "empleado_inexistente",
                        "El empleado no existe en la réplica local"
                ));

        if ("RETIRADO".equals(employee.getStatus())) {
            throw new VacationValidationException("empleado_retirado", "El empleado está retirado");
        }

        var conflicts = repository.findActiveOverlaps(request.empleadoId().trim(), request.fechaInicio(), request.fechaFin());
        if (!conflicts.isEmpty()) {
            throw new VacationConflictException(conflicts.get(0));
        }

        Vacation vacation = repository.save(new Vacation(
                "V-%d-%04d".formatted(today.getYear(), repository.nextNumber()),
                request.empleadoId().trim(),
                request.fechaInicio(),
                request.fechaFin(),
                Instant.now(clock)
        ));

        publisher.publishEvent(vacation);
        return VacationResponse.from(vacation);
    }

    @Transactional(readOnly = true)
    public VacationResponse get(String id) {
        return repository.findById(id)
                .map(VacationResponse::from)
                .orElseThrow(VacationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<VacationResponse> list(String employeeId) {
        var values = (employeeId == null)
                ? repository.findAll()
                : repository.findByEmployeeIdOrderByStartDateAsc(employeeId);

        return values.stream()
                .map(VacationResponse::from)
                .toList();
    }

    @Transactional
    public VacationResponse cancel(String id) {
        Vacation vacation = repository.findById(id)
                .orElseThrow(VacationNotFoundException::new);

        if (vacation.getStatus() != VacationStatus.PROGRAMADA) {
            throw new VacationStateException("Solo se puede cancelar un período PROGRAMADA");
        }

        vacation.cancel();
        return VacationResponse.from(vacation);
    }
}
