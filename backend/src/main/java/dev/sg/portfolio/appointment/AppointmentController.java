package dev.sg.portfolio.appointment;

import static dev.sg.portfolio.shared.web.ApiResponses.badGatewayResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.badRequestResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.okResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.promptLimitResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.realtimeErrorResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.realtimeTransportErrorResponse;
import static dev.sg.portfolio.shared.web.SseSupport.event;
import static dev.sg.portfolio.shared.web.SseSupport.textChunks;

import dev.sg.portfolio.domain.AgentTrace;
import dev.sg.portfolio.domain.AppointmentChatRequest;
import dev.sg.portfolio.domain.AppointmentSessionRequest;
import dev.sg.portfolio.domain.AppointmentToolEvent;
import dev.sg.portfolio.domain.AvailabilitySearchRequest;
import dev.sg.portfolio.domain.BookAppointmentRequest;
import dev.sg.portfolio.domain.DoneEvent;
import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.domain.RescheduleAppointmentRequest;
import dev.sg.portfolio.domain.SessionEvent;
import dev.sg.portfolio.domain.VoiceSessionResponse;
import dev.sg.portfolio.service.ClientIpResolver;
import dev.sg.portfolio.service.FreeModelClient;
import dev.sg.portfolio.service.OpenAiRealtimeClient;
import dev.sg.portfolio.service.OpenAiRealtimeException;
import dev.sg.portfolio.usage.IpPromptLimitService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final OpenAiRealtimeClient realtime;
    private final FreeModelClient freeModel;
    private final ClientIpResolver clientIpResolver;
    private final IpPromptLimitService promptLimitService;
    private final AppointmentDemoService appointmentDemoService;
    private final AppointmentFreeChatService appointmentFreeChatService;

    public AppointmentController(
            OpenAiRealtimeClient realtime,
            FreeModelClient freeModel,
            ClientIpResolver clientIpResolver,
            IpPromptLimitService promptLimitService,
            AppointmentDemoService appointmentDemoService,
            AppointmentFreeChatService appointmentFreeChatService
    ) {
        this.realtime = realtime;
        this.freeModel = freeModel;
        this.clientIpResolver = clientIpResolver;
        this.promptLimitService = promptLimitService;
        this.appointmentDemoService = appointmentDemoService;
        this.appointmentFreeChatService = appointmentFreeChatService;
    }

    @PostMapping("/agent/appointment/session")
    public Mono<ResponseEntity<Object>> createAppointmentSession(
            @RequestBody AppointmentSessionRequest request,
            ServerHttpRequest serverRequest
    ) {
        String clientIp = clientIpResolver.resolve(serverRequest);
        PromptLimitStatus promptLimit = promptLimitService.voiceMinuteStatus(clientIp);
        if (realtime.configured() && !promptLimit.allowed()) {
            return Mono.just(promptLimitResponse(promptLimit));
        }

        String consultationType = request == null ? "" : request.consultationType();
        return realtime.createAppointmentSession(promptLimitService.safetyIdentifier(clientIp), consultationType)
                .flatMap(session -> reserveVoiceMinuteAndReturn(clientIp, session))
                .onErrorResume(OpenAiRealtimeException.class, error -> realtimeErrorResponse(error))
                .onErrorResume(WebClientRequestException.class, error -> realtimeTransportErrorResponse(error))
                .onErrorResume(IllegalArgumentException.class, error -> badRequestResponse(error))
                .onErrorResume(IllegalStateException.class, error -> badGatewayResponse(error));
    }

    @PostMapping(value = "/agent/appointment/free/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamFreeAppointment(@RequestBody AppointmentChatRequest request) {
        String message = request == null || request.message() == null ? "" : request.message().trim();
        String sessionId = request != null && StringUtils.hasText(request.sessionId())
                ? request.sessionId().trim()
                : UUID.randomUUID().toString();
        AppointmentFreeChatService.AppointmentFreeTurn turn = appointmentFreeChatService.prepare(request);

        Flux<ServerSentEvent<Object>> header = Flux.just(event("session", new SessionEvent(sessionId)));
        for (AgentTrace trace : turn.traces()) {
            header = Flux.concat(header, Flux.just(event("trace", trace)));
        }
        if (!"none".equals(turn.action())) {
            header = Flux.concat(header, Flux.just(
                    event("tool", new AppointmentToolEvent(turn.action(), turn.detail()))
            ));
        }

        return Flux.concat(
                header,
                freeAppointmentBody(message, turn),
                Flux.just(event("done", new DoneEvent(sessionId, false)))
        );
    }

    @GetMapping("/appointments/demo/schedule")
    public Mono<ResponseEntity<Object>> appointmentSchedule(
            @RequestParam(defaultValue = "15") int days,
            @RequestParam(required = false) String sessionId
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.schedule(sessionId, days))
                .cast(Object.class)
                .flatMap(response -> okResponse(response));
    }

    @GetMapping("/appointments/demo/activity")
    public Mono<ResponseEntity<Object>> appointmentActivity(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "12") int limit
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.activity(sessionId, limit))
                .cast(Object.class)
                .flatMap(response -> okResponse(response));
    }

    @PostMapping("/appointments/demo/tools/availability")
    public Mono<ResponseEntity<Object>> searchAppointmentAvailability(
            @RequestBody AvailabilitySearchRequest request
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.searchAvailability(request))
                .cast(Object.class)
                .flatMap(response -> okResponse(response))
                .onErrorResume(IllegalArgumentException.class, error -> badRequestResponse(error));
    }

    @PostMapping("/appointments/demo/tools/book")
    public Mono<ResponseEntity<Object>> bookAppointment(@RequestBody BookAppointmentRequest request) {
        return Mono.fromSupplier(() -> appointmentDemoService.book(request))
                .cast(Object.class)
                .flatMap(response -> okResponse(response))
                .onErrorResume(IllegalArgumentException.class, error -> badRequestResponse(error));
    }

    @PostMapping("/appointments/demo/tools/reschedule")
    public Mono<ResponseEntity<Object>> rescheduleAppointment(
            @RequestBody RescheduleAppointmentRequest request
    ) {
        return Mono.fromSupplier(() -> appointmentDemoService.reschedule(request))
                .cast(Object.class)
                .flatMap(response -> okResponse(response))
                .onErrorResume(IllegalArgumentException.class, error -> badRequestResponse(error));
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

    private Flux<ServerSentEvent<Object>> freeAppointmentBody(
            String message,
            AppointmentFreeChatService.AppointmentFreeTurn turn
    ) {
        Flux<ServerSentEvent<Object>> freeTrace = Flux.just(
                event("trace", new AgentTrace(
                        "Modelo gratuito local",
                        freeModel.configured()
                                ? "Request enviado a FastAPI/Ollama con " + freeModel.model() + " para la demo de turnos."
                                : "El cliente local no esta configurado; se usa respuesta deterministica de la demo de turnos.",
                        "fallback"
                ))
        );

        if (List.of("book", "reschedule", "pending", "error").contains(turn.action())) {
            return Flux.concat(
                    freeTrace,
                    textChunks(appointmentFreeChatService.voiceFriendlyReply(
                            turn.fallbackReply(),
                            turn.fallbackReply()
                    ))
            );
        }

        Flux<ServerSentEvent<Object>> chunks = freeModel.configured()
                ? freeModel.streamText(message, turn.instructions())
                .collectList()
                .flatMapMany(parts -> textChunks(
                        appointmentFreeChatService.voiceFriendlyReply(
                                String.join("", parts),
                                turn.fallbackReply()
                        )
                ))
                .onErrorResume(error -> textChunks(
                        appointmentFreeChatService.voiceFriendlyReply(
                                turn.fallbackReply(),
                                turn.fallbackReply()
                        )
                ))
                : textChunks(appointmentFreeChatService.voiceFriendlyReply(
                turn.fallbackReply(),
                turn.fallbackReply()
        ));

        return Flux.concat(freeTrace, chunks);
    }
}
