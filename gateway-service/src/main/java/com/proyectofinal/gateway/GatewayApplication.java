package com.proyectofinal.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import observability.SecretLoader;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SecretLoader.load("jwt_secret", "JWT_SECRET");
        SpringApplication.run(GatewayApplication.class, args);
    }
}
