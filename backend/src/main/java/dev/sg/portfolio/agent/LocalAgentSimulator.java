package dev.sg.portfolio.agent;

import dev.sg.portfolio.domain.AgentTrace;
import dev.sg.portfolio.domain.TextChunk;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class LocalAgentSimulator {

    private static final List<String> SEBASTIAN_REFERENCES = List.of(
            "sebastian",
            "gatica"
    );

    private static final List<String> PROFILE_TOPICS = List.of(
            "perfil",
            "experiencia",
            "laboral",
            "trayectoria",
            "stack",
            "tecnologia",
            "tecnologias",
            "habilidad",
            "habilidades",
            "aptitud",
            "aptitudes",
            "trabajo",
            "trabajos",
            "proyecto",
            "proyectos"
    );

    private static final List<String> EXPLICIT_PROFILE_REQUESTS = List.of(
            "perfil profesional",
            "experiencia laboral",
            "trayectoria profesional",
            "resumen profesional",
            "resumen del perfil",
            "stack tecnico",
            "habilidades principales",
            "aptitudes profesionales"
    );

    private static final List<String> CURRENT_PROJECT_HINTS = List.of(
            "este proyecto",
            "el proyecto",
            "repositorio",
            "repo",
            "codigo",
            "implementacion",
            "arquitectura",
            "demos"
    );

    public Flux<ServerSentEvent<Object>> stream(String message) {
        return stream(message, "No hay OPENAI_API_KEY configurada; respondo con una demo standalone segura.");
    }

    public Flux<ServerSentEvent<Object>> stream(String message, String fallbackReason) {
        return streamFallback(fallbackReason, buildAnswer(message, fallbackReason));
    }

    public Flux<ServerSentEvent<Object>> streamFreeModelFallback(
            String message,
            String model,
            String fallbackReason
    ) {
        return streamFallback(fallbackReason, buildFreeModelAnswer(message, model, fallbackReason));
    }

    private Flux<ServerSentEvent<Object>> streamFallback(
            String fallbackReason,
            String answer
    ) {
        List<ServerSentEvent<Object>> events = new ArrayList<>();
        events.add(event("trace", new AgentTrace(
                "Local simulator",
                fallbackReason,
                "fallback"
        )));
        events.add(event("trace", new AgentTrace(
                "Context gate",
                "Respuesta local del portfolio sin acceso a sistemas privados, repositorios ni archivos.",
                "done"
        )));

        for (String chunk : chunk(answer, 150)) {
            events.add(event("chunk", new TextChunk(chunk)));
        }

        return Flux.fromIterable(events).delayElements(Duration.ofMillis(90));
    }

    String buildAnswer(String message, String fallbackReason) {
        String input = message == null || message.isBlank()
                ? "Quiero ver como pensas una solucion."
                : message;
        if (shouldUseCvBackedProfileFallback(input)) {
            return localDemoFallbackIntro(fallbackReason) + "\n\n" + cvBackedProfileSummary();
        }

        return "Modo demo local: recibi tu mensaje, pero esta respuesta no viene del modelo live de OpenAI. "
                + "Motivo: " + fallbackReason + " "
                + "Para no cortar la experiencia, el backend activo una respuesta local de respaldo. "
                + "Cuando la conexion con OpenAI complete correctamente, este mismo chat responde con el modelo real por streaming. "
                + "Input recibido: \"" + input + "\".";
    }

    String buildFreeModelAnswer(String message, String model, String fallbackReason) {
        String input = message == null || message.isBlank()
                ? "Quiero ver como pensas una solucion."
                : message;
        String modelName = model == null || model.isBlank() ? "Qwen" : model;
        if (shouldUseCvBackedProfileFallback(input)) {
            return qwenFallbackIntro(modelName, fallbackReason) + "\n\n" + cvBackedProfileSummary();
        }

        return qwenFallbackIntro(modelName, fallbackReason) + " "
                + "Si me preguntas quien soy en este modo, debo decir que estoy usando el runtime Qwen del portfolio, "
                + "no OpenAI. Input recibido: \"" + input + "\".";
    }

    boolean shouldUseCvBackedProfileFallback(String input) {
        String normalized = normalizeForIntent(input);
        if (normalized.isBlank()) {
            return false;
        }

        boolean mentionsSebastian = containsAny(normalized, SEBASTIAN_REFERENCES);
        boolean hasProfileTopic = containsAny(normalized, PROFILE_TOPICS);
        boolean explicitProfileRequest = containsAny(normalized, EXPLICIT_PROFILE_REQUESTS)
                || containsCvReference(normalized)
                || normalized.contains("quien es sebastian")
                || normalized.contains("sobre sebastian");

        if (mentionsSebastian && hasProfileTopic) {
            return true;
        }

        if (explicitProfileRequest) {
            return !containsAny(normalized, CURRENT_PROJECT_HINTS) || mentionsSebastian || containsCvReference(normalized);
        }

        return false;
    }

    private String localDemoFallbackIntro(String fallbackReason) {
        return "Modo demo local: esta respuesta usa el resumen del CV cargado en el portfolio. "
                + "Motivo del fallback: " + fallbackReason;
    }

    private String qwenFallbackIntro(String modelName, String fallbackReason) {
        return "Modo Qwen local: el runtime seleccionado es Qwen (" + modelName + "), "
                + "pero esta respuesta salio del simulador local. Motivo: " + fallbackReason;
    }

    private String cvBackedProfileSummary() {
        return "Sebastian Gatica es Java Full Stack Developer con más de 2 años de experiencia profesional. "
                + "Su foco combina backend Java/Spring, frontend React/Next.js, microservicios, integraciones, "
                + "bases de datos y soluciones con IA aplicada.\n\n"
                + "Experiencia destacada:\n"
                + "- Bank S.A. (Oct 2023-Aug 2024): proyectos empresariales, migraciones de PHP/Laravel a Java/Spring, "
                + "Blade a React, gestión documental, firma digital, VPS Linux, Nginx y AWS S3/EC2.\n"
                + "- Proyecto Emplag (Jul 2024-Jan 2025): CRM a medida con TALL Stack y MySQL, geolocalización en tiempo real, "
                + "trazabilidad de procesos, inventario QR, facturación ARCA y documentos en AWS S3.\n"
                + "- CFOTECH S.R.L. (Feb 2026-Jul 2026): refactors de monolitos a microservicios, Google ADK, flujos multiagente, "
                + "prompt engineering, skills, RAG, APIs, integraciones, modelado de datos y mantenimiento evolutivo.\n\n"
                + "Stack principal: Java, Spring Boot, Spring MVC, Spring Data JPA, WebFlux, Reactor, R2DBC, PostgreSQL/pgvector, "
                + "MySQL, MariaDB, React, Next.js, TypeScript, Vite, TailwindCSS, Laravel, Docker, Nginx, Linux VPS, "
                + "AWS, Google Cloud, Terraform y CI/CD.";
    }

    private String normalizeForIntent(String input) {
        String lowerCase = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lowerCase, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private boolean containsAny(String value, List<String> needles) {
        return needles.stream().anyMatch(value::contains);
    }

    private boolean containsCvReference(String normalized) {
        return normalized.matches(".*\\bcv\\b.*")
                || normalized.contains("curriculum");
    }

    private List<String> chunk(String value, int size) {
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < value.length(); index += size) {
            chunks.add(value.substring(index, Math.min(index + size, value.length())));
        }
        return chunks;
    }

    private ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }
}
