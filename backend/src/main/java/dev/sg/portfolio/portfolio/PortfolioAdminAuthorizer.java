package dev.sg.portfolio.portfolio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PortfolioAdminAuthorizer {

    private static final String ADMIN_TOKEN_HEADER = "X-Sgdev-Portfolio-Admin-Token";

    private final String expectedToken;

    public PortfolioAdminAuthorizer(
            @Value("${portfolio.admin-token:${PORTFOLIO_USAGE_ADMIN_TOKEN:}}") String expectedToken
    ) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    public AuthorizationResult authorize(ServerHttpRequest request) {
        if (!StringUtils.hasText(expectedToken)) {
            return new AuthorizationResult(false, 503, "PORTFOLIO_ADMIN_TOKEN no esta configurado.");
        }

        String token = request.getHeaders().getFirst(ADMIN_TOKEN_HEADER);
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            token = authorization.substring("Bearer ".length()).trim();
        }

        if (!expectedToken.equals(token)) {
            return new AuthorizationResult(false, 401, "No autorizado.");
        }

        return new AuthorizationResult(true, 200, "ok");
    }

    public record AuthorizationResult(boolean allowed, int status, String message) {
    }
}
