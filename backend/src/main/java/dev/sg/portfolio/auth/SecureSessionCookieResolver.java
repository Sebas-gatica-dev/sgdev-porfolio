package dev.sg.portfolio.auth;

import java.util.List;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

/**
 * Cookie de sesión {@code SESSION} con {@code HttpOnly}, {@code SameSite=Lax} y {@code Path=/}.
 * Se marca {@code Secure} solo cuando la petición llega por HTTPS, de modo que el entorno local
 * en HTTP siga funcionando.
 */
public class SecureSessionCookieResolver implements WebSessionIdResolver {

    public static final String COOKIE_NAME = "SESSION";
    private static final String SECURE_SCHEME = "https";

    private final CookieWebSessionIdResolver delegate = new CookieWebSessionIdResolver();

    public SecureSessionCookieResolver() {
        delegate.setCookieName(COOKIE_NAME);
    }

    @Override
    public List<String> resolveSessionIds(ServerWebExchange exchange) {
        return delegate.resolveSessionIds(exchange);
    }

    @Override
    public void setSessionId(ServerWebExchange exchange, String id) {
        exchange.getResponse().addCookie(sessionCookie(exchange, id).build());
    }

    @Override
    public void expireSession(ServerWebExchange exchange) {
        exchange.getResponse().addCookie(sessionCookie(exchange, "").maxAge(0).build());
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(ServerWebExchange exchange, String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .secure(isSecure(exchange));
    }

    private boolean isSecure(ServerWebExchange exchange) {
        return SECURE_SCHEME.equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
    }
}
