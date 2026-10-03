package co.edu.uniquindio.auth_service.service.impl;

import co.edu.uniquindio.auth_service.service.AuthService;
import co.edu.uniquindio.auth_service.dtos.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Override
    public TokenDTO login(LoginDTO loginDTO) throws Exception {
        
        return null;
    }

    @Override
    public void recoverPassword(RecoverPasswordDTO recoverDTO) throws Exception {
        // TODO: Implementar lógica para generar Token de Recuperación y enviar evento a RabbitMQ
    }

    @Override
    public void resetPassword(ResetPasswordDTO resetDTO) throws Exception {
        // TODO: Implementar lógica para validar el Token de Recuperación, cambiar password en BD y notificar
    }

    @Override
    public void changePassword(String userId, ChangePasswordDTO changeDTO) throws Exception {
        // TODO: Implementar lógica para validar password antiguo y actualizar por el nuevo en BD
    }
}
