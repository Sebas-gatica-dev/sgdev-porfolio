package dev.sg.portfolio.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Credencial única del administrador del portfolio.
 *
 * <p>La contraseña en claro es opcional y el hash BCrypt tiene precedencia. Sin ninguna de las
 * dos, cualquier intento de login falla y el backend arranca igual. No expone ni registra los
 * valores configurados.</p>
 */
@Component
public class AdminCredentials {

    private final String username;
    private final String password;
    private final String passwordHash;
    private final PasswordEncoder passwordEncoder;

    public AdminCredentials(
            @Value("${portfolio.admin.username:sgdev-admin}") String username,
            @Value("${portfolio.admin.password:}") String password,
            @Value("${portfolio.admin.password-hash:}") String passwordHash,
            PasswordEncoder passwordEncoder
    ) {
        this.username = normalize(username);
        this.password = normalize(password);
        this.passwordHash = normalize(passwordHash);
        this.passwordEncoder = passwordEncoder;
    }

    public String username() {
        return username;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(password) || StringUtils.hasText(passwordHash);
    }

    /**
     * Valida usuario y contraseña con el mismo resultado para usuario inexistente, contraseña
     * incorrecta y credencial no configurada.
     */
    public boolean matches(String candidateUsername, String candidatePassword) {
        String user = candidateUsername == null ? "" : candidateUsername;
        String candidate = candidatePassword == null ? "" : candidatePassword;
        boolean userMatches = constantTimeEquals(username, user);
        boolean passwordMatches = matchesPassword(candidate);
        return userMatches && passwordMatches;
    }

    private boolean matchesPassword(String candidate) {
        if (StringUtils.hasText(passwordHash)) {
            return matchesHash(candidate);
        }
        if (StringUtils.hasText(password)) {
            return constantTimeEquals(password, candidate);
        }
        return false;
    }

    private boolean matchesHash(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        try {
            return passwordEncoder.matches(candidate, passwordHash);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String expected, String candidate) {
        return MessageDigest.isEqual(sha256(expected), sha256(candidate));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no esta disponible.", error);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
