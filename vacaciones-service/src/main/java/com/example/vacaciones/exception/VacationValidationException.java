package com.example.vacaciones.exception;

public class VacationValidationException extends RuntimeException {

    public final String code;

    public VacationValidationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
