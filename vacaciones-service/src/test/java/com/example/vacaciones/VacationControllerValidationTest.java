package com.example.vacaciones;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.vacaciones.controller.ApiExceptionHandler;
import com.example.vacaciones.controller.VacationController;

class VacationControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(new VacationController(null))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExplainWhenEndDateIsBeforeStartDate() throws Exception {
        String startDate = LocalDate.now().plusDays(2).toString();
        String endDate = LocalDate.now().plusDays(1).toString();
        String request = """
                {"empleadoId":"E001","fechaInicio":"%s","fechaFin":"%s"}
                """.formatted(startDate, endDate);

        mockMvc.perform(post("/vacaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validacion"))
                .andExpect(jsonPath("$.message").value("La fecha de fin debe ser posterior a la fecha de inicio"));
    }

    @Test
    void shouldExplainExpectedDateFormatWhenDateCannotBeParsed() throws Exception {
        mockMvc.perform(post("/vacaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empleadoId":"E001","fechaInicio":"2026-10-11","fechaFin":"2026-10-5"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validacion"))
                .andExpect(jsonPath("$.message").value(
                        "El cuerpo JSON es inválido o contiene valores con formato incorrecto (fechas: yyyy-MM-dd)"));
    }
}