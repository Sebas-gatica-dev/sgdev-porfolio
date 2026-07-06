package dev.sg.portfolio.usage;

import dev.sg.portfolio.domain.TokenGrantRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/usage")
public class UsageAdminController {

    private static final String ADMIN_TOKEN_HEADER = "X-Sgdev-Portfolio-Admin-Token";

    private final IpPromptLimitService promptLimitService;

    public UsageAdminController(IpPromptLimitService promptLimitService) {
        this.promptLimitService = promptLimitService;
    }

    @GetMapping("/ips")
    public ResponseEntity<Object> listIps(ServerHttpRequest request) {
        ResponseEntity<Object> rejected = rejectIfUnauthorized(request);
        if (rejected != null) {
            return rejected;
        }

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "items", promptLimitService.adminRows()
        ));
    }

    @PostMapping("/grant")
    public ResponseEntity<Object> grantTokens(
            @RequestBody TokenGrantRequest grantRequest,
            ServerHttpRequest request
    ) {
        ResponseEntity<Object> rejected = rejectIfUnauthorized(request);
        if (rejected != null) {
            return rejected;
        }

        try {
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "item", promptLimitService.grantTokens(grantRequest)
            ));
        } catch (IllegalArgumentException error) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "ok", false,
                            "message", error.getMessage()
                    ));
        }
    }

    private ResponseEntity<Object> rejectIfUnauthorized(ServerHttpRequest request) {
        String expected = promptLimitService.adminToken();
        if (!StringUtils.hasText(expected)) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "ok", false,
                            "message", "PORTFOLIO_USAGE_ADMIN_TOKEN no esta configurado en el backend."
                    ));
        }

        String token = request.getHeaders().getFirst(ADMIN_TOKEN_HEADER);
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            token = authorization.substring("Bearer ".length()).trim();
        }

        if (!expected.equals(token)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "ok", false,
                            "message", "No autorizado."
                    ));
        }

        return null;
    }
}
