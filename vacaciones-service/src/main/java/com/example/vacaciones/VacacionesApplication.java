package com.example.vacaciones;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import observability.SecretLoader;

@SpringBootApplication
@EnableScheduling
public class VacacionesApplication {

    public static void main(String[] args) {
        SecretLoader.load("db_vacaciones_password", "DB_PASSWORD");
        SecretLoader.load("broker_password", "RABBITMQ_PASSWORD");
        SpringApplication.run(VacacionesApplication.class, args);
    }

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
