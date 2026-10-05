package com.example.vacaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VacationScheduler {

    private static final Logger log = LoggerFactory.getLogger(VacationScheduler.class);

    private final VacationService vacations;

    public VacationScheduler(VacationService vacations) {
        this.vacations = vacations;
    }

    @Scheduled(cron = "${vacaciones.scheduler.cron}")
    public void advanceDueVacations() {
        int transitioned = vacations.advanceDueVacations();
        if (transitioned > 0) {
            log.info("Vacation scheduler transitioned {} period(s)", transitioned);
        }
    }
}
