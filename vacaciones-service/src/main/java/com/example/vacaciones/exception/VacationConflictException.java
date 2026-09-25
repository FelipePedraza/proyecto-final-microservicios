package com.example.vacaciones.exception;

import com.example.vacaciones.domain.Vacation;

public class VacationConflictException extends RuntimeException {

    public final Vacation conflict;

    public VacationConflictException(Vacation conflict) {
        super("El período solicitado se solapa con otro período activo");
        this.conflict = conflict;
    }
}
