package dev.sg.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sg.portfolio.config.FreeModelProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
public class FreeModelClient {

    private final WebClient webClient;
    private final FreeModelProperties properties;
    private final ObjectMapper objectMapper;

    public FreeModelClient(
            @Qualifier("freeModelWebClient") WebClient freeModelWebClient,
            FreeModelProperties properties,
            ObjectMapper objectMapper
    ) {
        this.webClient = freeModelWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return properties.configured();
    }

    public String model() {
        return StringUtils.hasText(properties.model()) ? properties.model() : "qwen3:0.6b";
    }

    public Flux<String> streamText(String message, String instructions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message == null ? "" : message);
        payload.put("instructions", instructions == null ? "" : instructions);
        payload.put("model", model());

        return webClient.post()
                .uri("/chat/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException(
                                "Free model HTTP " + response.statusCode().value() + ": " + body
                        )))
                .bodyToFlux(String.class)
                .handle((data, sink) -> {
                    String delta = extractDelta(data);
                    if (StringUtils.hasText(delta)) {
                        sink.next(delta);
                    }
                });
    }

    private String extractDelta(String data) {
        if (!StringUtils.hasText(data)) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            String type = root.path("type").asText();
            if ("error".equals(type)) {
                String message = root.path("message").asText("El modelo gratuito local devolvio un error.");
                throw new IllegalStateException(message);
            }
            if ("chunk".equals(type)) {
                return root.path("text").asText();
            }
        } catch (Exception error) {
            if (error instanceof IllegalStateException) {
                throw (IllegalStateException) error;
            }
        }

        return null;
    }
}
