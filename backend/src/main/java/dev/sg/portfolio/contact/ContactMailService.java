package dev.sg.portfolio.contact;

import dev.sg.portfolio.config.ContactMailProperties;
import dev.sg.portfolio.domain.ContactMessageRequest;
import dev.sg.portfolio.domain.ContactMessageResponse;
import dev.sg.portfolio.domain.PromptLimitStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ContactMailService {

    private static final int MAX_FIELD_LENGTH = 160;
    private static final int MAX_MESSAGE_LENGTH = 2400;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ContactMailProperties properties;
    private final WebClient mailtrapWebClient;

    public ContactMailService(
            ContactMailProperties properties,
            @Qualifier("mailtrapWebClient") WebClient mailtrapWebClient
    ) {
        this.properties = properties;
        this.mailtrapWebClient = mailtrapWebClient;
    }

    public Mono<ContactMessageResponse> submit(ContactMessageRequest request) {
        ContactMessageRequest safeRequest = request == null
                ? new ContactMessageRequest("", "", "", "")
                : request;

        String name = required(trim(safeRequest.name()), "El nombre es obligatorio.");
        String email = required(trim(safeRequest.email()), "El email es obligatorio.");
        String company = trim(safeRequest.company());
        String message = required(trim(safeRequest.message()), "El mensaje es obligatorio.");

        validateLength(name, MAX_FIELD_LENGTH, "El nombre es demasiado largo.");
        validateLength(email, MAX_FIELD_LENGTH, "El email es demasiado largo.");
        validateLength(company, MAX_FIELD_LENGTH, "La empresa es demasiado larga.");
        validateLength(message, MAX_MESSAGE_LENGTH, "El mensaje es demasiado largo.");

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("El email no tiene un formato valido.");
        }

        if (!properties.mailtrapReady()) {
            return Mono.just(new ContactMessageResponse(
                    "mailtrap_pending",
                    "Mensaje recibido en modo local. La conexion con Mailtrap queda pendiente de credenciales.",
                    false
            ));
        }

        return mailtrapWebClient.post()
                .uri("/send")
                .bodyValue(mailtrapPayload(name, email, company, message))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException(
                                "Mailtrap HTTP " + response.statusCode().value() + ": " + body
                        )))
                .bodyToMono(String.class)
                .map(ignored -> new ContactMessageResponse(
                        "sent",
                        "Mensaje enviado. Te respondo por email.",
                        true
                ));
    }

    public Mono<ContactMessageResponse> submitTokenRequest(String clientIp, PromptLimitStatus status) {
        String safeIp = StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";

        if (!properties.mailtrapReady()) {
            return Mono.just(new ContactMessageResponse(
                    "mailtrap_pending",
                    "Solicitud registrada. El envio por email queda pendiente de credenciales Mailtrap.",
                    false
            ));
        }

        return mailtrapWebClient.post()
                .uri("/send")
                .bodyValue(tokenRequestPayload(safeIp, status))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException(
                                "Mailtrap HTTP " + response.statusCode().value() + ": " + body
                        )))
                .bodyToMono(String.class)
                .map(ignored -> new ContactMessageResponse(
                        "sent",
                        "Solicitud enviada. Te habilito mas tokens desde el admin.",
                        true
                ));
    }

    private Map<String, Object> mailtrapPayload(String name, String email, String company, String message) {
        String companyLine = StringUtils.hasText(company) ? company : "Sin empresa indicada";
        String subject = "Nuevo mensaje desde el portfolio - " + name;
        String text = """
                Nuevo mensaje desde el portfolio SGDev.

                Nombre: %s
                Email: %s
                Empresa: %s

                Mensaje:
                %s
                """.formatted(name, email, companyLine, message);
        String html = """
                <h2>Nuevo mensaje desde el portfolio SGDev</h2>
                <p><strong>Nombre:</strong> %s</p>
                <p><strong>Email:</strong> %s</p>
                <p><strong>Empresa:</strong> %s</p>
                <p><strong>Mensaje:</strong></p>
                <p>%s</p>
                """.formatted(
                escapeHtml(name),
                escapeHtml(email),
                escapeHtml(companyLine),
                escapeHtml(message).replace("\n", "<br>")
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", Map.of(
                "email", properties.fromAddress(),
                "name", "SGDev Portfolio"
        ));
        payload.put("to", List.of(Map.of("email", properties.toAddress())));
        payload.put("subject", subject);
        payload.put("text", text);
        payload.put("html", html);
        payload.put("category", "portfolio_contact");
        return payload;
    }

    private Map<String, Object> tokenRequestPayload(String clientIp, PromptLimitStatus status) {
        String subject = "Solicitud de mas tokens OpenAI - " + clientIp;
        String text = """
                La IP %s solicita mas tokens para la demo OpenAI.

                Tokens usados: %d
                Tokens restantes: %d
                Limite actual: %d
                Voz usada: %d segundos de %d
                """.formatted(
                clientIp,
                status.used(),
                status.remaining(),
                status.maxTokens(),
                status.voiceSecondsUsed(),
                status.maxVoiceSeconds()
        );
        String html = """
                <h2>Solicitud de mas tokens OpenAI</h2>
                <p><strong>IP:</strong> %s</p>
                <p><strong>Tokens usados:</strong> %d</p>
                <p><strong>Tokens restantes:</strong> %d</p>
                <p><strong>Limite actual:</strong> %d</p>
                <p><strong>Voz usada:</strong> %d segundos de %d</p>
                """.formatted(
                escapeHtml(clientIp),
                status.used(),
                status.remaining(),
                status.maxTokens(),
                status.voiceSecondsUsed(),
                status.maxVoiceSeconds()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", Map.of(
                "email", properties.fromAddress(),
                "name", "SGDev Portfolio"
        ));
        payload.put("to", List.of(Map.of("email", properties.toAddress())));
        payload.put("subject", subject);
        payload.put("text", text);
        payload.put("html", html);
        payload.put("category", "portfolio_token_request");
        return payload;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private void validateLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
