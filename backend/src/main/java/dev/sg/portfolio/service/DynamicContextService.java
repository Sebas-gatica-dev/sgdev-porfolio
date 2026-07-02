package dev.sg.portfolio.service;

import dev.sg.portfolio.domain.DynamicContextItem;
import dev.sg.portfolio.domain.DynamicContextRequest;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DynamicContextService {

    private static final int MAX_SOURCES = 5;
    private static final int MAX_OUTPUT_CHARS = 4_000;
    private static final int DEFAULT_TIMEOUT_MS = 4_000;
    private static final int MAX_TIMEOUT_MS = 10_000;

    private final WebClient webClient;

    public DynamicContextService() {
        this.webClient = WebClient.builder().build();
    }

    public Mono<List<DynamicContextItem>> collect(List<DynamicContextRequest> requests) {
        List<DynamicContextRequest> safeRequests = requests == null ? List.of() : requests;
        List<DynamicContextRequest> limited = safeRequests.stream().limit(MAX_SOURCES).toList();
        if (limited.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(limited)
                .index()
                .flatMap(tuple -> resolve(tuple.getT1().intValue(), tuple.getT2()))
                .collectList();
    }

    private Mono<DynamicContextItem> resolve(int index, DynamicContextRequest request) {
        String type = normalizeType(request.type());
        String name = normalizeName(request.name(), index);

        return switch (type) {
            case "time_now" -> Mono.just(new DynamicContextItem(
                    name,
                    type,
                    true,
                    "Hora UTC actual: " + OffsetDateTime.now(ZoneOffset.UTC)
            ));
            case "params_echo" -> Mono.just(new DynamicContextItem(
                    name,
                    type,
                    true,
                    "Parametros: " + safeParams(request.params())
            ));
            case "http_get" -> fetchHttpGet(name, type, request);
            default -> Mono.just(new DynamicContextItem(
                    name,
                    type,
                    false,
                    "Tipo no soportado. Usa time_now, params_echo o http_get."
            ));
        };
    }

    private Mono<DynamicContextItem> fetchHttpGet(String name, String type, DynamicContextRequest request) {
        if (!StringUtils.hasText(request.url())) {
            return Mono.just(new DynamicContextItem(name, type, false, "Falta url para http_get."));
        }
        String url = request.url().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Mono.just(new DynamicContextItem(name, type, false, "La url debe comenzar con http:// o https://"));
        }

        URI uri;
        try {
            uri = uriWithParams(url, request.params());
        } catch (IllegalArgumentException error) {
            return Mono.just(new DynamicContextItem(
                    name,
                    type,
                    false,
                    "URL invalida para http_get: " + url
            ));
        }
        return webClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException(
                                "HTTP " + response.statusCode().value() + ": " + trimBody(body)
                        )))
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeoutMs(request.timeoutMs())))
                .map(body -> new DynamicContextItem(name, type, true, trimBody(body)))
                .onErrorResume(error -> Mono.just(new DynamicContextItem(
                        name,
                        type,
                        false,
                        "Error al consultar " + url + ": " + error.getMessage()
                )));
    }

    private URI uriWithParams(String url, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }

    private String safeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        List<String> entries = new ArrayList<>();
        params.forEach((key, value) -> entries.add(key + "=" + value));
        return String.join(", ", entries);
    }

    private int timeoutMs(Integer requested) {
        if (requested == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.max(500, Math.min(MAX_TIMEOUT_MS, requested));
    }

    private String normalizeName(String value, int index) {
        if (!StringUtils.hasText(value)) {
            return "source-" + (index + 1);
        }
        return value.trim();
    }

    private String normalizeType(String value) {
        if (!StringUtils.hasText(value)) {
            return "http_get";
        }
        return value.trim().toLowerCase();
    }

    private String trimBody(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.trim();
        if (normalized.length() <= MAX_OUTPUT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_OUTPUT_CHARS) + "...";
    }
}
