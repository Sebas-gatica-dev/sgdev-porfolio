package dev.sg.portfolio.agent;

import static dev.sg.portfolio.shared.web.ApiResponses.badGatewayResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.okResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.promptLimitResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.realtimeErrorResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.realtimeTransportErrorResponse;
import static dev.sg.portfolio.shared.web.SseSupport.event;
import static dev.sg.portfolio.shared.web.SseSupport.textChunks;

import dev.sg.portfolio.domain.AgentTrace;
import dev.sg.portfolio.domain.ChatStreamRequest;
import dev.sg.portfolio.domain.DoneEvent;
import dev.sg.portfolio.domain.FreeModelOffer;
import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.domain.SessionEvent;
import dev.sg.portfolio.domain.TextChunk;
import dev.sg.portfolio.domain.VoiceSessionResponse;
import dev.sg.portfolio.service.ClientIpResolver;
import dev.sg.portfolio.service.FreeModelClient;
import dev.sg.portfolio.service.OpenAiResponsesClient;
import dev.sg.portfolio.service.OpenAiRealtimeException;
import dev.sg.portfolio.service.OpenAiRealtimeClient;
import dev.sg.portfolio.usage.IpPromptLimitService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class AgentController {

    private final OpenAiResponsesClient openAi;
    private final FreeModelClient freeModel;
    private final OpenAiRealtimeClient realtime;
    private final LocalAgentSimulator simulator;
    private final AgentRouter agentRouter;
    private final PromptComposerService promptComposerService;
    private final DynamicContextService dynamicContextService;
    private final ClientIpResolver clientIpResolver;
    private final IpPromptLimitService promptLimitService;

    public AgentController(
            OpenAiResponsesClient openAi,
            FreeModelClient freeModel,
            OpenAiRealtimeClient realtime,
            LocalAgentSimulator simulator,
            AgentRouter agentRouter,
            PromptComposerService promptComposerService,
            DynamicContextService dynamicContextService,
            ClientIpResolver clientIpResolver,
            IpPromptLimitService promptLimitService
    ) {
        this.openAi = openAi;
        this.freeModel = freeModel;
        this.realtime = realtime;
        this.simulator = simulator;
        this.agentRouter = agentRouter;
        this.promptComposerService = promptComposerService;
        this.dynamicContextService = dynamicContextService;
        this.clientIpResolver = clientIpResolver;
        this.promptLimitService = promptLimitService;
    }

    @PostMapping(value = "/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(
            @RequestBody ChatStreamRequest request,
            ServerHttpRequest serverRequest
    ) {
        String message = request.message() == null ? "" : request.message().trim();
        String sessionId = StringUtils.hasText(request.sessionId())
                ? request.sessionId()
                : UUID.randomUUID().toString();
        boolean requestedFreeRuntime = "free".equals(normalizeRuntime(request.runtime()));
        boolean freeRuntime = requestedFreeRuntime || !openAi.configured();
        String clientIp = clientIpResolver.resolve(serverRequest);
        PromptLimitStatus promptLimit = freeRuntime
                ? promptLimitService.status(clientIp)
                : promptLimitService.reservePrompt(clientIp);
        if (!freeRuntime && !promptLimit.allowed()) {
            return promptLimitExceeded(sessionId, promptLimit);
        }

        var route = agentRouter.resolve(message, request.agentId());
        AtomicBoolean live = new AtomicBoolean(!freeRuntime && openAi.configured());

        return dynamicContextService.collect(request.dynamicContext())
                .flatMapMany(contextItems -> {
                    PromptPlan promptPlan = promptComposerService.compose(
                            route,
                            safeList(request.extensions()),
                            contextItems
                    );
                    PromptPlan runtimePromptPlan = applyRuntimeIdentity(promptPlan, freeRuntime);
                    Flux<ServerSentEvent<Object>> header = runtimeHeader(sessionId, route, promptLimit, runtimePromptPlan);

                    Flux<ServerSentEvent<Object>> body = chatBody(message, runtimePromptPlan, live, freeRuntime);

                    return Flux.concat(
                            header,
                            body,
                            Flux.defer(() -> Flux.just(event("done", new DoneEvent(sessionId, live.get()))))
                    );
                });
    }

    @PostMapping("/agent/voice/session")
    public Mono<ResponseEntity<Object>> createVoiceSession(ServerHttpRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        PromptLimitStatus promptLimit = promptLimitService.voiceMinuteStatus(clientIp);
        if (realtime.configured() && !promptLimit.allowed()) {
            return Mono.just(promptLimitResponse(promptLimit));
        }

        return realtime.createTranscriptionSession(promptLimitService.safetyIdentifier(clientIp))
                .flatMap(session -> reserveVoiceMinuteAndReturn(clientIp, session))
                .onErrorResume(OpenAiRealtimeException.class, error -> realtimeErrorResponse(error))
                .onErrorResume(WebClientRequestException.class, error -> realtimeTransportErrorResponse(error))
                .onErrorResume(IllegalStateException.class, error -> badGatewayResponse(error));
    }

    @PostMapping("/agent/conversation/session")
    public Mono<ResponseEntity<Object>> createConversationSession(ServerHttpRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        PromptLimitStatus promptLimit = promptLimitService.voiceMinuteStatus(clientIp);
        if (realtime.configured() && !promptLimit.allowed()) {
            return Mono.just(promptLimitResponse(promptLimit));
        }

        return realtime.createConversationSession(promptLimitService.safetyIdentifier(clientIp))
                .flatMap(session -> reserveVoiceMinuteAndReturn(clientIp, session))
                .onErrorResume(OpenAiRealtimeException.class, error -> realtimeErrorResponse(error))
                .onErrorResume(WebClientRequestException.class, error -> realtimeTransportErrorResponse(error))
                .onErrorResume(IllegalStateException.class, error -> badGatewayResponse(error));
    }

    private Mono<ResponseEntity<Object>> reserveVoiceMinuteAndReturn(String clientIp, VoiceSessionResponse session) {
        if (!realtime.configured()) {
            return okResponse(session);
        }

        PromptLimitStatus promptLimit = promptLimitService.reserveVoiceMinute(clientIp);
        if (!promptLimit.allowed()) {
            return Mono.just(promptLimitResponse(promptLimit));
        }
        return okResponse(session);
    }

    private Flux<ServerSentEvent<Object>> runtimeHeader(
            String sessionId,
            dev.sg.portfolio.domain.AgentRoute route,
            PromptLimitStatus promptLimit,
            PromptPlan promptPlan
    ) {
        Flux<ServerSentEvent<Object>> header = Flux.just(
                event("session", new SessionEvent(sessionId)),
                event("agent", route),
                event("trace", new AgentTrace(
                        "Portfolio assistant runtime",
                        "Ruta activa: " + route.name() + ". " + route.reason(),
                        "running"
                )),
                event("trace", new AgentTrace(
                        "Prompt composer",
                        "Prompt: " + promptPlan.agentPromptPath()
                                + extensionSummary(promptPlan)
                                + contextSummary(promptPlan),
                        "connected"
                ))
        );
        if (promptLimit.enabled()) {
            header = Flux.concat(header, Flux.just(
                    event("prompt_limit", promptLimit),
                    event("trace", new AgentTrace(
                            "Limite por IP",
                            "Tokens usados: " + promptLimit.used() + " de " + promptLimit.maxTokens()
                                    + ". Quedan " + promptLimit.remaining() + ".",
                            "running"
                    ))
            ));
        }
        return header;
    }

    private Flux<ServerSentEvent<Object>> openAiBody(
            String message,
            PromptPlan promptPlan,
            AtomicBoolean live
    ) {
        Flux<ServerSentEvent<Object>> openAiTrace = Flux.just(
                event("trace", new AgentTrace(
                        "OpenAI Responses API",
                        "Request streaming enviado con instruction compuesta y store=false.",
                        "connected"
                ))
        );

        Flux<ServerSentEvent<Object>> chunks = openAi.streamText(message, promptPlan.instructions())
                .map(text -> event("chunk", new TextChunk(text)))
                .onErrorResume(error -> {
                    live.set(false);
                    return simulator.stream(
                            message,
                            "la llamada live a OpenAI no pudo completarse (" + error.getMessage() + ")."
                    );
                });

        return Flux.concat(openAiTrace, chunks);
    }

    private Flux<ServerSentEvent<Object>> chatBody(
            String message,
            PromptPlan promptPlan,
            AtomicBoolean live,
            boolean freeRuntime
    ) {
        String directContactAnswer = directContactAnswer(message);
        if (directContactAnswer != null) {
            live.set(false);
            return Flux.concat(
                    Flux.just(event("trace", new AgentTrace(
                            "Portfolio contact links",
                            "Respuesta directa con enlaces canonicos del portfolio.",
                            "done"
                    ))),
                    textChunks(directContactAnswer)
            );
        }
        if (freeRuntime) {
            return freeModelBody(message, promptPlan, live);
        }
        return openAi.configured()
                ? openAiBody(message, promptPlan, live)
                : simulator.stream(message);
    }

    private Flux<ServerSentEvent<Object>> freeModelBody(
            String message,
            PromptPlan promptPlan,
            AtomicBoolean live
    ) {
        live.set(false);
        Flux<ServerSentEvent<Object>> freeTrace = Flux.just(
                event("trace", new AgentTrace(
                        "Modelo gratuito local",
                        freeModel.configured()
                                ? "Request enviado a FastAPI/Ollama con " + freeModel.model() + "."
                                : "El cliente local no esta configurado; se usa fallback demo.",
                        "fallback"
                ))
        );

        Flux<ServerSentEvent<Object>> identityPrefix = freeRuntimeIdentityPrefix(message);

        Flux<ServerSentEvent<Object>> chunks = freeModel.configured()
                ? freeModel.streamText(message, promptPlan.instructions())
                        .map(text -> event("chunk", new TextChunk(text)))
                        .onErrorResume(error -> simulator.streamFreeModelFallback(
                                message,
                                freeModel.model(),
                                "el modelo gratuito local no pudo responder (" + error.getMessage() + ")."
                        ))
                : simulator.streamFreeModelFallback(
                        message,
                        freeModel.model(),
                        "el modelo gratuito local no esta configurado en PORTFOLIO_FREE_MODEL_BASE_URL."
                );

        return Flux.concat(freeTrace, identityPrefix, chunks);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String extensionSummary(PromptPlan promptPlan) {
        if (promptPlan.loadedExtensions().isEmpty() && promptPlan.missingExtensions().isEmpty()) {
            return ". Sin extensiones";
        }
        String loaded = promptPlan.loadedExtensions().isEmpty()
                ? "ninguna"
                : String.join(", ", promptPlan.loadedExtensions());
        String missing = promptPlan.missingExtensions().isEmpty()
                ? ""
                : ". Faltantes: " + String.join(", ", promptPlan.missingExtensions());
        return ". Extensiones: " + loaded + missing;
    }

    private String contextSummary(PromptPlan promptPlan) {
        if (promptPlan.dynamicContextItems().isEmpty()) {
            return ". Sin contexto dinamico";
        }
        long ok = promptPlan.dynamicContextItems().stream().filter(dev.sg.portfolio.domain.DynamicContextItem::success).count();
        return ". Contexto dinamico: " + ok + "/" + promptPlan.dynamicContextItems().size() + " fuentes ok";
    }

    private Flux<ServerSentEvent<Object>> promptLimitExceeded(String sessionId, PromptLimitStatus promptLimit) {
        return Flux.just(
                event("session", new SessionEvent(sessionId)),
                event("prompt_limit", promptLimit),
                event("trace", new AgentTrace(
                        "Limite por IP",
                        "La IP ya uso " + promptLimit.used() + " de " + promptLimit.maxTokens() + " tokens.",
                        "fallback"
                )),
                event("free_model_offer", freeModelOffer()),
                event("chunk", new TextChunk(
                        "Agotaste tu demo gratuita para esta IP. Cada IP tiene "
                                + promptLimit.maxTokens()
                                + " tokens OpenAI: cada interaccion usa "
                                + promptLimit.chatTokenCost()
                                + " tokens y cada llamada de voz dura como maximo "
                                + promptLimit.voiceSessionSeconds()
                                + " segundos. Podes solicitar mas tokens o seguir con el modelo gratuito local."
                )),
                event("done", new DoneEvent(sessionId, false))
        );
    }

    private FreeModelOffer freeModelOffer() {
        return new FreeModelOffer(
                freeModel.configured(),
                "free",
                freeModel.model(),
                "Demo gratuita agotada",
                "Podes continuar con el modelo gratuito local. Usa " + freeModel.model()
                        + " en el VPS y no consume tokens de OpenAI."
        );
    }

    private String normalizeRuntime(String runtime) {
        return runtime == null ? "openai" : runtime.trim().toLowerCase();
    }

    private PromptPlan applyRuntimeIdentity(PromptPlan promptPlan, boolean freeRuntime) {
        if (!freeRuntime) {
            return promptPlan;
        }

        String model = freeModel.model();
        String runtimeIdentity = """

                ## Runtime Actual (Prioridad Alta)
                - Estas respondiendo con Qwen (%s), ejecutado como modelo gratuito local mediante FastAPI/Ollama.
                - Si el usuario pregunta quien sos, que modelo usas o que proveedor esta activo, deci claramente que esta respuesta esta usando Qwen (%s).
                - No digas que sos OpenAI ni que estas impulsado por OpenAI cuando el runtime actual es Qwen; podes mencionar que el portfolio tambien ofrece OpenAI como opcion separada.
                """.formatted(model, model);

        return new PromptPlan(
                runtimeIdentity + "\n\n" + promptPlan.instructions(),
                promptPlan.agentPromptPath(),
                promptPlan.loadedExtensions(),
                promptPlan.missingExtensions(),
                promptPlan.dynamicContextItems()
        );
    }

    private Flux<ServerSentEvent<Object>> freeRuntimeIdentityPrefix(String message) {
        if (!isIdentityQuestion(message)) {
            return Flux.empty();
        }

        return Flux.just(event("chunk", new TextChunk(
                "Estoy usando Qwen (" + freeModel.model()
                        + ") como modelo gratuito local integrado al portfolio de Sebastian Gatica. "
        )));
    }

    private boolean isIdentityQuestion(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }

        String normalized = java.text.Normalizer.normalize(message, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        return normalized.contains("quien sos")
                || normalized.contains("quien eres")
                || normalized.contains("que sos")
                || normalized.contains("que eres")
                || normalized.contains("que modelo")
                || normalized.contains("cual modelo")
                || normalized.contains("modelo usas")
                || normalized.contains("estas usando")
                || normalized.contains("sos openai")
                || normalized.contains("eres openai");
    }

    private String directContactAnswer(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        String normalized = java.text.Normalizer.normalize(message, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        boolean asksLinkedIn = normalized.contains("linkedin") || normalized.contains("linked in");
        boolean asksGithub = normalized.contains("github") || normalized.contains("git hub");
        if (!asksLinkedIn && !asksGithub) {
            return null;
        }

        List<String> links = new ArrayList<>();
        if (asksLinkedIn) {
            links.add("LinkedIn de Sebastian: [Sebastian Gatica](https://ar.linkedin.com/in/sebastian-gatica-dev)");
        }
        if (asksGithub) {
            links.add("GitHub de Sebastian: [Sebas-gatica-dev](https://github.com/Sebas-gatica-dev)");
        }
        return String.join("\n", links);
    }

}
