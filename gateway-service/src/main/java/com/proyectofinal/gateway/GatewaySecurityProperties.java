package com.proyectofinal.gateway;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(List<String> publicPaths) {

    public GatewaySecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        if (publicPaths.isEmpty()) {
            throw new IllegalArgumentException("gateway.security.public-paths no puede estar vacío");
        }
    }
}
