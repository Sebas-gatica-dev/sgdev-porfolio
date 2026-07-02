package dev.sg.portfolio.service;

import dev.sg.portfolio.domain.AgentTrace;
import dev.sg.portfolio.domain.TextChunk;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class LocalAgentSimulator {

    public Flux<ServerSentEvent<Object>> stream(String message) {
        return stream(message, "No hay OPENAI_API_KEY configurada; respondo con una demo standalone segura.");
    }

    public Flux<ServerSentEvent<Object>> stream(String message, String fallbackReason) {
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

        for (String chunk : chunk(buildAnswer(message, fallbackReason), 150)) {
            events.add(event("chunk", new TextChunk(chunk)));
        }

        return Flux.fromIterable(events).delayElements(Duration.ofMillis(90));
    }

    private String buildAnswer(String message, String fallbackReason) {
        String input = message == null || message.isBlank()
                ? "Quiero ver como pensas una solucion."
                : message;

        return "Modo demo local: recibi tu mensaje, pero esta respuesta no viene del modelo live de OpenAI. "
                + "Motivo: " + fallbackReason + " "
                + "Para no cortar la experiencia, el backend activo una respuesta local de respaldo. "
                + "Cuando la conexion con OpenAI complete correctamente, este mismo chat responde con el modelo real por streaming. "
                + "Input recibido: \"" + input + "\".";
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
