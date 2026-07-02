package dev.sg.portfolio.controller;

import dev.sg.portfolio.domain.AgentTrace;
import dev.sg.portfolio.domain.AppointmentSessionRequest;
import dev.sg.portfolio.domain.AvailabilitySearchRequest;
import dev.sg.portfolio.domain.BookAppointmentRequest;
import dev.sg.portfolio.domain.ChatStreamRequest;
import dev.sg.portfolio.domain.DoneEvent;
import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.domain.RescheduleAppointmentRequest;
import dev.sg.portfolio.domain.SessionEvent;
import dev.sg.portfolio.domain.TextChunk;
import dev.sg.portfolio.domain.VoiceSessionResponse;
import dev.sg.portfolio.service.AgentRouter;
import dev.sg.portfolio.service.AppointmentDemoService;
import dev.sg.portfolio.service.ClientIpResolver;
import dev.sg.portfolio.service.DynamicContextService;
import dev.sg.portfolio.service.IpPromptLimitService;
import dev.sg.portfolio.service.LocalAgentSimulator;
import dev.sg.portfolio.service.OpenAiResponsesClient;
import dev.sg.portfolio.service.OpenAiRealtimeException;
import dev.sg.portfolio.service.OpenAiRealtimeClient;
import dev.sg.portfolio.service.PdfSummaryException;
import dev.sg.portfolio.service.PdfSummaryService;
import dev.sg.portfolio.service.PromptComposerService;
import dev.sg.portfolio.service.PromptPlan;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class AgentController {

    private final OpenAiResponsesClient openAi;
    private final OpenAiRealtimeClient realtime;
    private final LocalAgentSimulator simulator;
    private final AgentRouter agentRouter;
    private final PromptComposerService promptComposerService;
    private final DynamicContextService dynamicContextService;
    private final ClientIpResolver clientIpResolver;
    private final IpPromptLimitService promptLimitService;
    private final PdfSummaryService pdfSummaryService;
    private final AppointmentDemoService appointmentDemoService;

    public AgentController(
            OpenAiResponsesClient openAi,
            OpenAiRealtimeClient realtime,
            LocalAgentSimulator simulator,
            AgentRouter agentRouter,
            PromptComposerService promptComposerService,
            DynamicContextService dynamicContextService,
            ClientIpResolver clientIpResolver,
            IpPromptLimitService promptLimitService,
            PdfSummaryService pdfSummaryService,
            AppointmentDemoService appointmentDemoService
    ) {
        this.openAi = openAi;
        this.realtime = realtime;
        this.simulator = simulator;
        this.agentRouter = agentRouter;
        this.promptComposerService = promptComposerService;
        this.dynamicContextService = dynamicContextService;
        this.clientIpResolver = clientIpResolver;
        this.promptLimitService = promptLimitService;
        this.pdfSummaryService = pdfSummaryService;
        this.appointmentDemoService = appointmentDemoService;
    }

    @GetMapping("/portfolio/health")
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "mode", openAi.configured() ? "portfolio-runtime-live" : "portfolio-runtime-local",
                "openaiConfigured", openAi.configured(),
                "voiceConfigured", realtime.configured(),
                "promptLimitEnabled", promptLimitService.enabled(),
                "promptLimitMaxPrompts", promptLimitService.maxPrompts(),
                "runtime", "Portfolio assistant + demo tools + prompt composer + voice"
        );
    }

    @GetMapping("/portfolio/blueprint")
    public Map<String, Object> blueprint() {
        return Map.of(
                "name", "SG AI Agent Portfolio",
                "backend", "Spring Boot WebFlux",
                "runtime", "Portfolio assistant architecture",
                "llm", "OpenAI Responses API + Realtime API",
                "architecture", List.of(
                        "coordinator router",
                        "specialist agents",
                        "function tools",
                        "dynamic context",
                        "prompt library",
                        "human confirmation gates",
                        "streaming traces"
                ),
                "upstream", "standalone portfolio runtime"
        );
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
        PromptLimitStatus promptLimit = promptLimitService.reservePrompt(clientIpResolver.resolve(serverRequest));
        if (!promptLimit.allowed()) {
            return promptLimitExceeded(sessionId, promptLimit);
        }

        var route = agentRouter.resolve(message, request.agentId());
        AtomicBoolean live = new AtomicBoolean(openAi.configured());

        return dynamicContextService.collect(request.dynamicContext())
                .flatMapMany(contextItems -> {
                    PromptPlan promptPlan = promptComposerService.compose(
                            route,
                            safeList(request.extensions()),
                            contextItems
                    );
                    Flux<ServerSentEvent<Object>> header = runtimeHeader(sessionId, route, promptLimit, promptPlan);

                    Flux<ServerSentEvent<Object>> body = openAi.configured()
                            ? openAiBody(message, promptPlan, live)
                            : simulator.stream(message);

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
        if (realtime.configured()) {
            PromptLimitStatus promptLimit = promptLimitService.reserveVoiceMinute(clientIp);
            if (!promptLimit.allowed()) {
                return Mono.just(promptLimitResponse(promptLimit));
            }
        }

        return realtime.createTranscriptionSession(promptLimitService.safetyIdentifier(clientIp))
                .flatMap(this::okResponse)
                .onErrorResume(OpenAiRealtimeException.class, this::realtimeErrorResponse)
                .onErrorResume(WebClientRequestException.class, this::realtimeTransportErrorResponse)
                .onErrorResume(IllegalStateException.class, this::badGatewayResponse);
    }

    @PostMapping("/agent/conversation/session")
    public Mono<ResponseEntity<Object>> createConversationSession(ServerHttpRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        if (realtime.configured()) {
            PromptLimitStatus promptLimit = promptLimitService.reserveVoiceMinute(clientIp);
            if (!promptLimit.allowed()) {
                return Mono.just(promptLimitResponse(promptLimit));
            }
        }

        return realtime.createConversationSession(promptLimitService.safetyIdentifier(clientIp))
                .flatMap(this::okResponse)
                .onErrorResume(OpenAiRealtimeException.class, this::realtimeErrorResponse)
                .onErrorResume(WebClientRequestException.class, this::realtimeTransportErrorResponse)
                .onErrorResume(IllegalStateException.class, this::badGatewayResponse);
    }

    @PostMapping("/agent/appointment/session")
    public Mono<ResponseEntity<Object>> createAppointmentSession(
            @RequestBody AppointmentSessionRequest request,
            ServerHttpRequest serverRequest
    ) {
        String clientIp = clientIpResolver.resolve(serverRequest);
        if (realtime.configured()) {
            PromptLimitStatus promptLimit = promptLimitService.reserveVoiceMinute(clientIp);
            if (!promptLimit.allowed()) {
                return Mono.just(promptLimitResponse(promptLimit));
            }
        }

        String consultationType = request == null ? "" : request.consultationType();
        return realtime.createAppointmentSession(promptLimitService.safetyIdentifier(clientIp), consultationType)
                .flatMap(this::okResponse)
                .onErrorResume(OpenAiRealtimeException.class, this::realtimeErrorResponse)
                .onErrorResume(WebClientRequestException.class, this::realtimeTransportErrorResponse)
                .onErrorResume(IllegalArgumentException.class, this::badRequestResponse)
                .onErrorResume(IllegalStateException.class, this::badGatewayResponse);
    }

    @GetMapping("/appointments/demo/schedule")
    public Mono<ResponseEntity<Object>> appointmentSchedule(
            @RequestParam(defaultValue = "15") int days,
            @RequestParam(required = false) String sessionId
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.schedule(sessionId, days))
                .cast(Object.class)
                .flatMap(this::okResponse);
    }

    @GetMapping("/appointments/demo/activity")
    public Mono<ResponseEntity<Object>> appointmentActivity(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "12") int limit
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.activity(sessionId, limit))
                .cast(Object.class)
                .flatMap(this::okResponse);
    }

    @PostMapping("/appointments/demo/tools/availability")
    public Mono<ResponseEntity<Object>> searchAppointmentAvailability(
            @RequestBody AvailabilitySearchRequest request
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.searchAvailability(request))
                .cast(Object.class)
                .flatMap(this::okResponse)
                .onErrorResume(IllegalArgumentException.class, this::badRequestResponse);
    }

    @PostMapping("/appointments/demo/tools/book")
    public Mono<ResponseEntity<Object>> bookAppointment(@RequestBody BookAppointmentRequest request) {
        return Mono.fromSupplier(() -> appointmentDemoService.book(request))
                .cast(Object.class)
                .flatMap(this::okResponse)
                .onErrorResume(IllegalArgumentException.class, this::badRequestResponse);
    }

    @PostMapping("/appointments/demo/tools/reschedule")
    public Mono<ResponseEntity<Object>> rescheduleAppointment(
            @RequestBody RescheduleAppointmentRequest request
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.reschedule(request))
                .cast(Object.class)
                .flatMap(this::okResponse)
                .onErrorResume(IllegalArgumentException.class, this::badRequestResponse);
    }

    @PostMapping(
            value = "/agent/document/summary",
            consumes = MediaType.APPLICATION_PDF_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<Object>> summarizePdf(ServerHttpRequest request) {
        PromptLimitStatus promptLimit = promptLimitService.reservePrompt(clientIpResolver.resolve(request));
        if (!promptLimit.allowed()) {
            return Mono.just(promptLimitResponse(promptLimit));
        }

        return pdfSummaryService.summarize(request)
                .cast(Object.class)
                .flatMap(this::okResponse)
                .onErrorResume(PdfSummaryException.class, this::pdfSummaryErrorResponse)
                .onErrorResume(IllegalStateException.class, this::badGatewayResponse);
    }

    private Mono<ResponseEntity<Object>> okResponse(Object response) {
        return Mono.just(ResponseEntity.ok(response));
    }

    private Mono<ResponseEntity<Object>> realtimeErrorResponse(OpenAiRealtimeException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatusCode.valueOf(error.statusCode()))
                .body(Map.of(
                        "status", error.statusCode(),
                        "message", error.getMessage()
                )));
    }

    private Mono<ResponseEntity<Object>> pdfSummaryErrorResponse(PdfSummaryException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatusCode.valueOf(error.statusCode()))
                .body(Map.of(
                        "status", error.statusCode(),
                        "message", error.getMessage()
                )));
    }

    private Mono<ResponseEntity<Object>> badGatewayResponse(IllegalStateException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "status", HttpStatus.BAD_GATEWAY.value(),
                        "message", error.getMessage()
                )));
    }

    private Mono<ResponseEntity<Object>> realtimeTransportErrorResponse(WebClientRequestException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "status", HttpStatus.BAD_GATEWAY.value(),
                        "message", "No pude abrir la sesion de voz porque la conexion con OpenAI se corto. Reintenta."
                )));
    }

    private Mono<ResponseEntity<Object>> badRequestResponse(IllegalArgumentException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "message", error.getMessage()
                )));
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
            header = Flux.concat(header, Flux.just(event("trace", new AgentTrace(
                    "Limite por IP",
                    "Creditos usados: " + promptLimit.used() + " de " + promptLimit.maxPrompts()
                            + ". Quedan " + promptLimit.remaining() + ".",
                    "running"
            ))));
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
                event("trace", new AgentTrace(
                        "Limite por IP",
                        "La IP ya uso " + promptLimit.used() + " de " + promptLimit.maxPrompts() + " creditos.",
                        "fallback"
                )),
                event("chunk", new TextChunk(
                        "Agotaste tu demo gratuita para esta IP. Cada IP tiene "
                                + promptLimit.maxPrompts()
                                + " creditos: chat usa 1 y voz usa 5 por minuto. Si estas probando en local, pon "
                                + "PORTFOLIO_IP_PROMPT_LIMIT_ENABLED=false."
                )),
                event("done", new DoneEvent(sessionId, false))
        );
    }

    private ResponseEntity<Object> promptLimitResponse(PromptLimitStatus promptLimit) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "message", "Agotaste tu demo gratuita para esta IP. Cada IP tiene "
                                + promptLimit.maxPrompts()
                                + " creditos: chat usa 1 y voz usa 5 por minuto."
                ));
    }

    private ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }
}
