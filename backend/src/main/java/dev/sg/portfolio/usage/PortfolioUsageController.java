package dev.sg.portfolio.usage;

import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.contact.ContactMailService;
import dev.sg.portfolio.service.ClientIpResolver;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/portfolio/usage")
public class PortfolioUsageController {

    private final ClientIpResolver clientIpResolver;
    private final IpPromptLimitService promptLimitService;
    private final ContactMailService contactMailService;

    public PortfolioUsageController(
            ClientIpResolver clientIpResolver,
            IpPromptLimitService promptLimitService,
            ContactMailService contactMailService
    ) {
        this.clientIpResolver = clientIpResolver;
        this.promptLimitService = promptLimitService;
        this.contactMailService = contactMailService;
    }

    @GetMapping("/status")
    public PromptLimitStatus status(ServerHttpRequest request) {
        return promptLimitService.status(clientIpResolver.resolve(request));
    }

    @PostMapping("/token-request")
    public Mono<ResponseEntity<Object>> requestMoreTokens(ServerHttpRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        PromptLimitStatus status = promptLimitService.requestMoreTokens(clientIp);
        return contactMailService.submitTokenRequest(clientIp, status)
                .cast(Object.class)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalStateException.class, error -> Mono.just(ResponseEntity
                        .status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of(
                                "status", HttpStatus.BAD_GATEWAY.value(),
                                "message", "La solicitud quedo registrada, pero Mailtrap no pudo enviar el email."
                        ))));
    }
}
