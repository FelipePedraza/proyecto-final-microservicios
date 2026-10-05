package co.edu.uniquindio.auth_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWTUtilsTest {

    private JWTUtils utils(String secret) {
        JWTUtils jwt = new JWTUtils();
        ReflectionTestUtils.setField(jwt, "secretKeyString", secret);
        return jwt;
    }

    @Test
    void elHeaderIncluyeAlgoritmoYTipoDeToken() {
        String token = utils("clave-de-pruebas-de-al-menos-32-bytes-0123456789")
                .generateToken("E001", Map.of("role", "USER"), 60_000);

        String header = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))), StandardCharsets.UTF_8);

        assertTrue(header.contains("\"alg\":\"HS256\""), header);
        assertTrue(header.contains("\"typ\":\"JWT\""), header);
    }

    @Test
    void cadaTokenLlevaUnJtiUnico() {
        JWTUtils jwt = utils("clave-de-pruebas-de-al-menos-32-bytes-0123456789");
        String a = jwt.generateToken("E001", Map.of("role", "USER"), 60_000);
        String b = jwt.generateToken("E001", Map.of("role", "USER"), 60_000);

        String payloadA = new String(Base64.getUrlDecoder().decode(a.split("[.]")[1]), StandardCharsets.UTF_8);
        String payloadB = new String(Base64.getUrlDecoder().decode(b.split("[.]")[1]), StandardCharsets.UTF_8);

        assertTrue(payloadA.contains("\"jti\":\""), payloadA);
        assertNotEquals(payloadA, payloadB);
    }

    @Test
    void elResetTokenSigueValidandoseConSuTipoDeClaim() throws Exception {
        JWTUtils jwt = utils("clave-de-pruebas-de-al-menos-32-bytes-0123456789");
        String reset = jwt.generateToken("E001", Map.of("type", "RESET_PASSWORD"), 60_000);
        String access = jwt.generateToken("E001", Map.of("role", "USER"), 60_000);

        assertEquals("E001", jwt.validateAndGetSubject(reset, "RESET_PASSWORD"));
        assertThrows(Exception.class, () -> jwt.validateAndGetSubject(access, "RESET_PASSWORD"));
    }

    @Test
    void unSecretCortoSeRechaza() {
        assertThrows(IllegalStateException.class, () -> utils("corto").generateToken("E001", Map.of(), 1000));
    }
}
