package dev.sg.portfolio.contact;

import dev.sg.portfolio.domain.ContactMessageRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactMailService contactMailService;

    public ContactController(ContactMailService contactMailService) {
        this.contactMailService = contactMailService;
    }

    @PostMapping("/message")
    public Mono<ResponseEntity<Object>> submit(@RequestBody ContactMessageRequest request) {
        try {
            return contactMailService.submit(request)
                    .cast(Object.class)
                    .map(ResponseEntity::ok)
                    .onErrorResume(IllegalStateException.class, this::mailtrapErrorResponse);
        } catch (IllegalArgumentException error) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", HttpStatus.BAD_REQUEST.value(),
                            "message", error.getMessage()
                    )));
        }
    }

    private Mono<ResponseEntity<Object>> mailtrapErrorResponse(IllegalStateException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "status", HttpStatus.BAD_GATEWAY.value(),
                        "message", "Mailtrap no pudo enviar el mensaje. Revisa el token, dominio verificado y remitente."
                )));
    }
}
