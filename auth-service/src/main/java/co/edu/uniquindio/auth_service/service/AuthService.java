package co.edu.uniquindio.auth_service.service;

import co.edu.uniquindio.auth_service.dtos.*;

public interface AuthService {
    TokenDTO login(LoginDTO loginDTO) throws Exception;
    void recoverPassword(RecoverPasswordDTO recoverDTO) throws Exception;
    void resetPassword(ResetPasswordDTO resetDTO) throws Exception;
    void changePassword(String userId, ChangePasswordDTO changeDTO) throws Exception;
}
