package dev.sg.portfolio.portfolio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Autorización por token del administrador para el API de proyectos.
 *
 * <p>El token vacío, no configurado o igual a un placeholder público nunca autoriza. La
 * ausencia de configuración responde 401, no 503.</p>
 */
@Component
public class PortfolioAdminAuthorizer {

    private static final String ADMIN_TOKEN_HEADER = "X-Sgdev-Portfolio-Admin-Token";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PLACEHOLDER_TOKEN = "change-me-admin-token";

    private final String expectedToken;

    public PortfolioAdminAuthorizer(
            @Value("${portfolio.admin-token:}") String projectToken,
            @Value("${portfolio.ip-prompt-limit.admin-token:}") String usageToken
    ) {
        String configuredProjectToken = usableToken(projectToken);
        this.expectedToken = configuredProjectToken != null ? configuredProjectToken : usableToken(usageToken);
    }

    public boolean isTokenConfigured() {
        return expectedToken != null;
    }

    public boolean hasValidToken(ServerHttpRequest request) {
        if (expectedToken == null) {
            return false;
        }
        String token = requestToken(request);
        return StringUtils.hasText(token) && constantTimeEquals(expectedToken, token);
    }

    public AuthorizationResult authorize(ServerHttpRequest request) {
        if (!hasValidToken(request)) {
            return new AuthorizationResult(false, 401, "No autorizado.");
        }
        return new AuthorizationResult(true, 200, "ok");
    }

    private static String usableToken(String candidate) {
        String token = candidate == null ? "" : candidate.trim();
        return token.isEmpty() || PLACEHOLDER_TOKEN.equals(token) ? null : token;
    }

    private String requestToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(ADMIN_TOKEN_HEADER);
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            token = authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return token;
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

    public record AuthorizationResult(boolean allowed, int status, String message) {
    }
}
