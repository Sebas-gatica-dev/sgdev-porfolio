package dev.sg.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import dev.sg.portfolio.config.IpPromptLimitProperties;
import dev.sg.portfolio.config.OpenAiProperties;
import dev.sg.portfolio.domain.VoiceSessionResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OpenAiRealtimeClient {

    private final WebClient webClient;
    private final OpenAiProperties properties;
    private final IpPromptLimitProperties promptLimitProperties;
    private final ObjectMapper objectMapper;
    private final PromptLibraryService promptLibraryService;

    public OpenAiRealtimeClient(
            @Qualifier("openAiWebClient") WebClient openAiWebClient,
            OpenAiProperties properties,
            IpPromptLimitProperties promptLimitProperties,
            ObjectMapper objectMapper,
            PromptLibraryService promptLibraryService
    ) {
        this.webClient = openAiWebClient;
        this.properties = properties;
        this.promptLimitProperties = promptLimitProperties;
        this.objectMapper = objectMapper;
        this.promptLibraryService = promptLibraryService;
    }

    public boolean configured() {
        return properties.configured();
    }

    public Mono<VoiceSessionResponse> createTranscriptionSession(String safetyIdentifier) {
        if (!configured()) {
            return Mono.error(new OpenAiRealtimeException(
                    503,
                    "OPENAI_API_KEY no esta configurada para activar voz en este backend."
            ));
        }

        return webClient.post()
                .uri("/realtime/client_secrets")
                .headers(headers -> {
                    if (StringUtils.hasText(safetyIdentifier)) {
                        headers.set("OpenAI-Safety-Identifier", safetyIdentifier);
                    }
                })
                .bodyValue(sessionPayload())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> realtimeError(response.statusCode().value(), body)))
                .bodyToMono(String.class)
                .map(body -> toSessionResponse(body, properties.voiceModel()));
    }

    public Mono<VoiceSessionResponse> createConversationSession(String safetyIdentifier) {
        return createConversationSession(safetyIdentifier, null);
    }

    public Mono<VoiceSessionResponse> createAppointmentSession(String safetyIdentifier, String consultationType) {
        return createConversationSession(
                safetyIdentifier,
                appointmentScenarioInstructions(consultationType),
                appointmentTools()
        );
    }

    private Mono<VoiceSessionResponse> createConversationSession(
            String safetyIdentifier,
            String instructionsOverride
    ) {
        return createConversationSession(safetyIdentifier, instructionsOverride, java.util.List.of());
    }

    private Mono<VoiceSessionResponse> createConversationSession(
            String safetyIdentifier,
            String instructionsOverride,
            java.util.List<Map<String, Object>> tools
    ) {
        if (!configured()) {
            return Mono.error(new OpenAiRealtimeException(
                    503,
                    "OPENAI_API_KEY no esta configurada para activar conversacion en este backend."
            ));
        }

        return webClient.post()
                .uri("/realtime/client_secrets")
                .headers(headers -> {
                    if (StringUtils.hasText(safetyIdentifier)) {
                        headers.set("OpenAI-Safety-Identifier", safetyIdentifier);
                    }
                })
                .bodyValue(conversationPayload(instructionsOverride, tools))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> realtimeError(response.statusCode().value(), body, properties.conversationModel())))
                .bodyToMono(String.class)
                .map(body -> toSessionResponse(body, properties.conversationModel()));
    }

    private Map<String, Object> sessionPayload() {
        Map<String, Object> transcription = new LinkedHashMap<>();
        transcription.put("model", properties.voiceModel());
        if (StringUtils.hasText(properties.voiceLanguage())) {
            transcription.put("language", properties.voiceLanguage());
        }
        if (StringUtils.hasText(properties.voicePrompt())) {
            transcription.put("prompt", properties.voicePrompt());
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("transcription", transcription);
        input.put("noise_reduction", Map.of("type", "near_field"));
        input.put("turn_detection", Map.of(
                "type", "server_vad",
                "threshold", 0.5,
                "prefix_padding_ms", 300,
                "silence_duration_ms", 500
        ));

        return Map.of(
                "expires_after", Map.of(
                        "anchor", "created_at",
                        "seconds", promptLimitProperties.voiceSessionSeconds()
                ),
                "session", Map.of(
                        "type", "transcription",
                        "audio", Map.of("input", input)
                )
        );
    }

    private OpenAiRealtimeException realtimeError(int statusCode, String body) {
        return realtimeError(statusCode, body, properties.voiceModel());
    }

    private Map<String, Object> conversationPayload(String instructionsOverride, java.util.List<Map<String, Object>> tools) {
        Map<String, Object> transcription = new LinkedHashMap<>();
        transcription.put("model", properties.voiceModel());
        if (StringUtils.hasText(properties.voiceLanguage())) {
            transcription.put("language", properties.voiceLanguage());
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("transcription", transcription);
        input.put("noise_reduction", Map.of("type", "near_field"));
        input.put("turn_detection", Map.of(
                "type", "server_vad",
                "threshold", 0.68,
                "prefix_padding_ms", 300,
                "silence_duration_ms", 850,
                "create_response", true,
                "interrupt_response", false
        ));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("voice", StringUtils.hasText(properties.conversationVoice())
                ? properties.conversationVoice()
                : "alloy");
        output.put("speed", 1.0);

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("type", "realtime");
        session.put("model", properties.conversationModel());
        session.put("instructions", StringUtils.hasText(instructionsOverride)
                ? instructionsOverride
                : conversationInstructions());
        session.put("output_modalities", java.util.List.of("audio"));
        session.put("audio", Map.of(
                "input", input,
                "output", output
        ));
        session.put("truncation", Map.of(
                "type", "retention_ratio",
                "retention_ratio", 0.8,
                "token_limits", Map.of("post_instructions", 6000)
        ));
        if (!tools.isEmpty()) {
            session.put("tools", tools);
            session.put("tool_choice", "auto");
        }

        return Map.of(
                "expires_after", Map.of(
                        "anchor", "created_at",
                        "seconds", promptLimitProperties.voiceSessionSeconds()
                ),
                "session", session
        );
    }

    private String conversationInstructions() {
        if (StringUtils.hasText(properties.conversationInstructions())) {
            return properties.conversationInstructions();
        }

        String model = StringUtils.hasText(properties.conversationModel())
                ? properties.conversationModel()
                : "el modelo configurado para conversacion de voz";
        return promptLibraryService.conversationBasePrompt()
                .replace("{{model}}", model);
    }

    private String appointmentScenarioInstructions(String consultationType) {
        String model = StringUtils.hasText(properties.conversationModel())
                ? properties.conversationModel()
                : "el modelo mini configurado para conversacion de voz";
        String appointmentPrompt = promptLibraryService.appointmentScenarioPrompt(normalizeConsultationType(consultationType))
                .replace("{{model}}", model);
        return """
                %s

                ## Contexto De Turnos Medicos
                %s
                """.formatted(conversationInstructions(), appointmentPrompt).trim();
    }

    private java.util.List<Map<String, Object>> appointmentTools() {
        return java.util.List.of(
                Map.of(
                        "type", "function",
                        "name", "find_available_appointments",
                        "description", "Busca horarios disponibles reales en la agenda medica de demo.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "consultation_type", Map.of(
                                                "type", "string",
                                                "enum", java.util.List.of("traumatology", "follow-up", "cardiology")
                                        ),
                                        "date_from", Map.of(
                                                "type", "string",
                                                "description", "Fecha inicial en formato YYYY-MM-DD."
                                        ),
                                        "date_to", Map.of(
                                                "type", "string",
                                                "description", "Fecha final en formato YYYY-MM-DD."
                                        ),
                                        "preferred_time_from", Map.of(
                                                "type", "string",
                                                "description", "Hora inicial preferida en formato HH:mm."
                                        ),
                                        "preferred_time_to", Map.of(
                                                "type", "string",
                                                "description", "Hora final preferida en formato HH:mm."
                                        )
                                ),
                                "required", java.util.List.of("consultation_type", "date_from", "date_to")
                        )
                ),
                Map.of(
                        "type", "function",
                        "name", "book_appointment",
                        "description", "Reserva un turno medico real dentro de la agenda de demo.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "consultation_type", Map.of(
                                                "type", "string",
                                                "enum", java.util.List.of("traumatology", "follow-up", "cardiology")
                                        ),
                                        "patient_name", Map.of(
                                                "type", "string",
                                                "description", "Nombre de pila del paciente."
                                        ),
                                        "start_at", Map.of(
                                                "type", "string",
                                                "description", "Fecha y hora exacta en formato YYYY-MM-DDTHH:mm:ss."
                                        )
                                ),
                                "required", java.util.List.of("consultation_type", "patient_name", "start_at")
                        )
                ),
                Map.of(
                        "type", "function",
                        "name", "reschedule_current_appointment",
                        "description", "Reprograma el turno activo de esta misma llamada.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "start_at", Map.of(
                                                "type", "string",
                                                "description", "Nueva fecha y hora exacta en formato YYYY-MM-DDTHH:mm:ss."
                                        )
                                ),
                                "required", java.util.List.of("start_at")
                        )
                )
        );
    }

    private String normalizeConsultationType(String consultationType) {
        if (!StringUtils.hasText(consultationType)) {
            return "traumatology";
        }
        String normalized = consultationType.trim().toLowerCase();
        return switch (normalized) {
            case "traumatology", "follow-up", "cardiology" -> normalized;
            default -> throw new IllegalArgumentException("Tipo de consulta no soportado.");
        };
    }

    private OpenAiRealtimeException realtimeError(int statusCode, String body, String modelName) {
        String upstreamMessage = extractOpenAiMessage(body);
        String model = StringUtils.hasText(modelName) ? modelName : "sin modelo";

        if (statusCode == 401) {
            return new OpenAiRealtimeException(
                    statusCode,
                    "OpenAI rechazo la API key de voz (401). Revisa OPENAI_API_KEY."
            );
        }

        if (statusCode == 403) {
            return new OpenAiRealtimeException(
                    statusCode,
                    "OpenAI rechazo la sesion de voz (403). Revisa que la API key tenga billing/permisos "
                            + "para Realtime y acceso al modelo " + model + detail(upstreamMessage)
            );
        }

        if (statusCode == 429) {
            return new OpenAiRealtimeException(
                    statusCode,
                    "OpenAI limito la sesion de voz (429). Hay que esperar o subir el limite del proyecto."
            );
        }

        return new OpenAiRealtimeException(
                statusCode,
                "OpenAI Realtime devolvio HTTP " + statusCode + detail(upstreamMessage)
        );
    }

    private String extractOpenAiMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText();
            return StringUtils.hasText(message) ? message : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String detail(String message) {
        return StringUtils.hasText(message) ? ". Detalle: " + message : ".";
    }

    private VoiceSessionResponse toSessionResponse(String body, String fallbackModel) {
        JsonNode root = parseJson(body);
        JsonNode secret = root.hasNonNull("client_secret") ? root.path("client_secret") : root;
        String value = secret.path("value").asText();
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("OpenAI no devolvio un client_secret usable");
        }

        long expiresAt = secret.path("expires_at").asLong(0L);
        String responseModel = root.path("session").path("model").asText();
        return new VoiceSessionResponse(
                value,
                expiresAt,
                StringUtils.hasText(responseModel) ? responseModel : fallbackModel,
                properties.realtimeWebrtcUrl()
        );
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception error) {
            throw new IllegalStateException("OpenAI devolvio una respuesta de voz no parseable", error);
        }
    }
}
