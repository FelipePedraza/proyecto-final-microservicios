package co.edu.uniquindio.auth_service.service;

import co.edu.uniquindio.auth_service.dtos.*;

public interface AuthService {
    TokenDTO login(LoginDTO loginDTO);
    void recoverPassword(RecoverPasswordDTO recoverDTO);
    void resetPassword(ResetPasswordDTO resetDTO);
    void changePassword(String userId, ChangePasswordDTO changeDTO);
}
