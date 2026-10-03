package co.edu.uniquindio.auth_service.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JWTUtils {

    @Value("${jwt.secret}")
    private String secretKeyString;

    private SecretKey getKey() {
        // En JWT modernos de io.jsonwebtoken, las llaves deben ser de un tamaño adecuado
        return Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un token JWT genérico.
     * @param subject El ID o correo del usuario.
     * @param claims Diccionario con información extra (ej. role, type).
     * @param expirationMillis Cuánto tiempo en milisegundos durará el token.
     */
    public String generateToken(String subject, Map<String, Object> claims, long expirationMillis) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + expirationMillis);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(exp)
                .signWith(getKey())
                .compact();
    }
}
