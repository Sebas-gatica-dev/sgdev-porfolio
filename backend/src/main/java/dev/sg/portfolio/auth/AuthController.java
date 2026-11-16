package dev.sg.portfolio.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminCredentials credentials;

    public AuthController(AdminCredentials credentials) {
        this.credentials = credentials;
    }

    @GetMapping("/session")
    public Mono<AdminSessionResponse> session(ServerWebExchange exchange) {
        return exchange.getSession()
                .map(session -> {
                    String username = session.getAttribute(AdminSessions.ADMIN_USERNAME_ATTRIBUTE);
                    return StringUtils.hasText(username)
                            ? new AdminSessionResponse(true, username)
                            : AdminSessionResponse.anonymous();
                })
                .defaultIfEmpty(AdminSessionResponse.anonymous());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(
            @RequestBody(required = false) LoginRequest request,
            ServerWebExchange exchange
    ) {
        String username = request == null ? null : request.username();
        String password = request == null ? null : request.password();
        if (!credentials.matches(username, password)) {
            ResponseEntity<Object> rejected = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AdminLoginErrorResponse.invalidCredentials());
            return Mono.just(rejected);
        }

        return exchange.getSession()
                .flatMap(session -> rotateIfStarted(session)
                        .then(Mono.fromSupplier(() -> {
                            session.getAttributes().put(
                                    AdminSessions.ADMIN_USERNAME_ATTRIBUTE,
                                    credentials.username()
                            );
                            ResponseEntity<Object> accepted = ResponseEntity.ok(
                                    new AdminSessionResponse(true, credentials.username())
                            );
                            return accepted;
                        })));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<AdminSessionResponse>> logout(ServerWebExchange exchange) {
        ResponseEntity<AdminSessionResponse> closed = ResponseEntity.ok(AdminSessionResponse.anonymous());
        return exchange.getSession()
                .flatMap(session -> session.invalidate().thenReturn(closed))
                .defaultIfEmpty(closed);
    }

    private Mono<Void> rotateIfStarted(WebSession session) {
        if (!session.isStarted()) {
            return Mono.empty();
        }
        return session.changeSessionId()
                .onErrorResume(error -> Mono.empty());
    }
}
