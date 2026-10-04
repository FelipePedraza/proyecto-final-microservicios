package com.proyectofinal.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import reactor.core.publisher.Mono;

class ProfileOwnershipAuthorizationManagerTest {

    private final ProfileOwnershipAuthorizationManager manager =
            new ProfileOwnershipAuthorizationManager();

    @Test
    void userPuedeModificarSuPropioPerfil() {
        assertTrue(decision("E001", "ROLE_USER", "E001"));
    }

    @Test
    void userNoPuedeModificarElPerfilDeOtroEmpleado() {
        assertFalse(decision("E001", "ROLE_USER", "E999"));
    }

    @Test
    void adminPuedeModificarCualquierPerfil() {
        assertTrue(decision("ADMIN-001", "ROLE_ADMIN", "E999"));
    }

    private boolean decision(String principal, String role, String employeeId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "token",
                List.of(new SimpleGrantedAuthority(role)));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/perfiles/" + employeeId).build());
        var context = new AuthorizationContext(
                exchange,
                Map.of("empleadoId", employeeId));

        return manager.authorize(Mono.just(authentication), context)
                .block()
                .isGranted();
    }
}
