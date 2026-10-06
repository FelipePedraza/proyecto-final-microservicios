package com.proyectofinal.gateway;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/** Autoriza a ADMIN o al USER dueño del perfil indicado en la ruta. */
@Component
public class ProfileOwnershipAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    @Override
    public Mono<AuthorizationResult> authorize(
            Mono<Authentication> authentication,
            AuthorizationContext context) {
        String employeeId = String.valueOf(context.getVariables().get("empleadoId"));

        return authentication
                .filter(current -> current.isAuthenticated())
                .map(current -> (AuthorizationResult) new AuthorizationDecision(
                        hasRole(current, "ROLE_ADMIN")
                                || current.getName().equals(employeeId)))
                .defaultIfEmpty((AuthorizationResult) new AuthorizationDecision(false));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
