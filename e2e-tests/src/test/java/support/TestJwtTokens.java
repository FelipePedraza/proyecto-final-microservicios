package support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Emite tokens temporales únicamente para activar cuentas dentro del entorno E2E. */
public final class TestJwtTokens {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private TestJwtTokens() {
    }

    public static String resetPasswordToken(String subject, String secret) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + 900;
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"type\":\"RESET_PASSWORD\",\"jti\":\"" + UUID.randomUUID()
                + "\",\"sub\":\"" + subject + "\",\"iat\":" + issuedAt + ",\"exp\":" + expiresAt + "}";
        String unsigned = encode(header) + "." + encode(payload);

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return unsigned + "." + BASE64_URL.encodeToString(hmac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("No fue posible emitir el token temporal E2E", error);
        }
    }

    private static String encode(String value) {
        return BASE64_URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
