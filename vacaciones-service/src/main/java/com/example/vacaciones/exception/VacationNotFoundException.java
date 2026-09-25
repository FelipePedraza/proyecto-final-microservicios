package com.example.vacaciones.exception;

public class VacationNotFoundException extends RuntimeException {

    public VacationNotFoundException() {
        super("Vacation not found");
    }
}
