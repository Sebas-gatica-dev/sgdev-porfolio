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
    public Flux<ServerSentEvent<Object>> streamFreeAppointment(
            @RequestBody AppointmentChatRequest request,
            ServerHttpRequest serverRequest
    ) {
        String message = request == null || request.message() == null ? "" : request.message().trim();
        String sessionId = appointmentSessionId(serverRequest, request == null ? "" : request.sessionId());
        AppointmentChatRequest effectiveRequest = new AppointmentChatRequest(
                message,
                sessionId,
                request == null ? "" : request.consultationType()
        );
        AppointmentFreeChatService.AppointmentFreeTurn turn = appointmentFreeChatService.prepare(effectiveRequest);

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
            @RequestParam(required = false) String sessionId,
            ServerHttpRequest serverRequest
    ) {
        String effectiveSessionId = appointmentSessionId(serverRequest, sessionId);
        return Mono.fromSupplier(() -> appointmentDemoService.schedule(effectiveSessionId, days))
                .cast(Object.class)
                .flatMap(response -> okResponse(response));
    }

    @GetMapping("/appointments/demo/activity")
    public Mono<ResponseEntity<Object>> appointmentActivity(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "12") int limit,
            ServerHttpRequest serverRequest
    ) {
        String effectiveSessionId = appointmentSessionId(serverRequest, sessionId);
        return Mono.fromSupplier(() -> appointmentDemoService.activity(effectiveSessionId, limit))
                .cast(Object.class)
                .flatMap(response -> okResponse(response));
    }

    @PostMapping("/appointments/demo/tools/availability")
    public Mono<ResponseEntity<Object>> searchAppointmentAvailability(
            @RequestBody AvailabilitySearchRequest request,
            ServerHttpRequest serverRequest
    ) {
        AvailabilitySearchRequest effectiveRequest = new AvailabilitySearchRequest(
                appointmentSessionId(serverRequest, request == null ? "" : request.sessionId()),
                request == null ? "" : request.consultationType(),
                request == null ? "" : request.dateFrom(),
                request == null ? "" : request.dateTo(),
                request == null ? "" : request.preferredTimeFrom(),
                request == null ? "" : request.preferredTimeTo()
        );
        return Mono.fromSupplier(() -> appointmentDemoService.searchAvailability(effectiveRequest))
                .cast(Object.class)
                .flatMap(response -> okResponse(response))
                .onErrorResume(IllegalArgumentException.class, error -> badRequestResponse(error));
    }

    @PostMapping("/appointments/demo/tools/book")
    public Mono<ResponseEntity<Object>> bookAppointment(
            @RequestBody BookAppointmentRequest request,
            ServerHttpRequest serverRequest
    ) {
        BookAppointmentRequest effectiveRequest = new BookAppointmentRequest(
                appointmentSessionId(serverRequest, request == null ? "" : request.sessionId()),
                request == null ? "" : request.consultationType(),
                request == null ? "" : request.patientName(),
                request == null ? "" : request.startAt()
        );
        return Mono.fromSupplier(() -> appointmentDemoService.book(effectiveRequest))
                .cast(Object.class)
                .flatMap(response -> okResponse(response))
                .onErrorResume(IllegalArgumentException.class, error -> badRequestResponse(error));
    }

    @PostMapping("/appointments/demo/tools/reschedule")
    public Mono<ResponseEntity<Object>> rescheduleAppointment(
            @RequestBody RescheduleAppointmentRequest request,
            ServerHttpRequest serverRequest
    ) {
        RescheduleAppointmentRequest effectiveRequest = new RescheduleAppointmentRequest(
                appointmentSessionId(serverRequest, request == null ? "" : request.sessionId()),
                request == null ? "" : request.startAt()
        );
        return Mono.fromSupplier(() -> appointmentDemoService.reschedule(effectiveRequest))
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

    private String appointmentSessionId(ServerHttpRequest request, String fallbackSessionId) {
        String clientIp = clientIpResolver.resolve(request);
        if (StringUtils.hasText(clientIp) && !"unknown".equalsIgnoreCase(clientIp)) {
            return promptLimitService.safetyIdentifier(clientIp);
        }
        if (StringUtils.hasText(fallbackSessionId)) {
            return fallbackSessionId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private Flux<ServerSentEvent<Object>> freeAppointmentBody(
            String message,
            AppointmentFreeChatService.AppointmentFreeTurn turn
    ) {
        Flux<ServerSentEvent<Object>> freeTrace = Flux.just(
                event("trace", new AgentTrace(
                        "Modelo gratuito local",
                        freeModel.configured()
                                ? "Qwen queda asistido por reglas de turnos para evitar respuestas inestables."
                                : "El cliente local no esta configurado; se usa respuesta deterministica de la demo de turnos.",
                        "fallback"
                ))
        );

        Flux<ServerSentEvent<Object>> chunks = textChunks(appointmentFreeChatService.voiceFriendlyReply(
                turn.fallbackReply(),
                turn.fallbackReply()
        ));

        return Flux.concat(freeTrace, chunks);
    }
}
