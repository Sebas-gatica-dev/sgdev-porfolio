package dev.sg.portfolio;

import dev.sg.portfolio.auth.AdminSessions;
import dev.sg.portfolio.portfolio.PortfolioAdminAuthorizer;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Protege únicamente {@code /api/admin/projects} y sus subrutas.
 *
 * <p>Acepta una sesión de administrador válida o un token admin configurado. Sin ninguno de los
 * dos responde 401 JSON, nunca 503. {@code /api/admin/usage/**} queda fuera de este filtro y
 * conserva su autorización por token.</p>
 */
@Component
public class PortfolioAdminAccessFilter implements WebFilter, Ordered {

    private static final PathPattern PROJECTS_PATH =
            PathPatternParser.defaultInstance.parse("/api/admin/projects/**");
    private static final String UNAUTHORIZED_BODY = "{\"ok\":false,\"message\":\"No autorizado.\"}";

    private final PortfolioAdminAuthorizer authorizer;

    public PortfolioAdminAccessFilter(PortfolioAdminAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!requiresAdmin(exchange)) {
            return chain.filter(exchange);
        }
        if (authorizer.hasValidToken(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        return hasAdminSession(exchange)
                .flatMap(authenticated -> authenticated ? chain.filter(exchange) : unauthorized(exchange));
    }

    private boolean requiresAdmin(ServerWebExchange exchange) {
        return PROJECTS_PATH.matches(exchange.getRequest().getPath().pathWithinApplication());
    }

    private Mono<Boolean> hasAdminSession(ServerWebExchange exchange) {
        return exchange.getSession()
                .map(session -> {
                    String username = session.getAttribute(AdminSessions.ADMIN_USERNAME_ATTRIBUTE);
                    return StringUtils.hasText(username);
                })
                .defaultIfEmpty(false);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = UNAUTHORIZED_BODY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
