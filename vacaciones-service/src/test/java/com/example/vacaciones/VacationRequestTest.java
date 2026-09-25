package com.example.vacaciones;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.vacaciones.dto.VacationRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class VacationRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final LocalDate today = LocalDate.now();

    @Test
    void shouldRejectInvalidVacationRequests() {
        assertFalse(validator.validate(new VacationRequest("", null, null)).isEmpty());
        assertFalse(validator.validate(new VacationRequest("e", today.minusDays(1), today)).isEmpty());
        assertFalse(validator.validate(new VacationRequest("e", today.plusDays(2), today.plusDays(1))).isEmpty());
        assertFalse(validator.validate(new VacationRequest("e", today.plusDays(1), null)).isEmpty());
    }

    @Test
    void shouldAcceptValidVacationRequest() {
        assertTrue(validator.validate(new VacationRequest("e", today.plusDays(1), today.plusDays(2))).isEmpty());
    }
}
