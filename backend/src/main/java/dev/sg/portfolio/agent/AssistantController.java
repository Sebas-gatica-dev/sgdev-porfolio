package dev.sg.portfolio.agent;

import static dev.sg.portfolio.shared.web.SseSupport.event;

import dev.sg.portfolio.domain.AssistantChatRequest;
import dev.sg.portfolio.domain.TextChunk;
import dev.sg.portfolio.service.FreeModelClient;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final int MAXIMUM_PROMPT_CHARS = 4_000;
    private static final String INSTRUCTIONS = """
            Sos el asistente de ayuda del portfolio de Sebastian Gatica. Responde en el idioma del
            usuario, de forma concisa y basandote en el contexto recuperado. Si el contexto no
            contiene la respuesta, indicalo con honestidad. No inventes experiencia, proyectos,
            enlaces ni datos personales.
            """;

    private final FreeModelClient freeModel;
    private final PromptLibraryService prompts;

    public AssistantController(FreeModelClient freeModel, PromptLibraryService prompts) {
        this.freeModel = freeModel;
        this.prompts = prompts;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@RequestBody AssistantChatRequest request) {
        String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
        if (!StringUtils.hasText(prompt)) {
            return Flux.just(error("El mensaje es obligatorio."));
        }
        if (prompt.length() > MAXIMUM_PROMPT_CHARS) {
            return Flux.just(error("El mensaje supera el limite permitido."));
        }
        if (!freeModel.configured()) {
            return Flux.just(error("El asistente no esta disponible en este momento."));
        }

        String sessionId = request != null && StringUtils.hasText(request.sessionId())
                ? request.sessionId().trim().substring(0, Math.min(request.sessionId().trim().length(), 128))
                : UUID.randomUUID().toString();

        return Flux.concat(
                        freeModel.streamText(prompt, prompts.corePrompt(), sessionId)
                                .map(text -> event("chunk", new TextChunk(text))),
                        Flux.just(event("done", Map.of("type", "done", "sessionId", sessionId)))
                )
                .onErrorResume(ignored -> Flux.just(error("El asistente no esta disponible en este momento.")));
    }

    private ServerSentEvent<Object> error(String message) {
        return event("error", Map.of("type", "error", "message", message));
    }
}
