package dev.sg.portfolio.shared.web;

import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.document.PdfSummaryException;
import dev.sg.portfolio.service.OpenAiRealtimeException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

public final class ApiResponses {

    private ApiResponses() {
    }

    public static Mono<ResponseEntity<Object>> okResponse(Object response) {
        return Mono.just(ResponseEntity.ok(response));
    }

    public static Mono<ResponseEntity<Object>> realtimeErrorResponse(OpenAiRealtimeException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatusCode.valueOf(error.statusCode()))
                .body(Map.of(
                        "status", error.statusCode(),
                        "message", error.getMessage()
                )));
    }

    public static Mono<ResponseEntity<Object>> pdfSummaryErrorResponse(PdfSummaryException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatusCode.valueOf(error.statusCode()))
                .body(Map.of(
                        "status", error.statusCode(),
                        "message", error.getMessage()
                )));
    }

    public static Mono<ResponseEntity<Object>> badGatewayResponse(IllegalStateException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "status", HttpStatus.BAD_GATEWAY.value(),
                        "message", error.getMessage()
                )));
    }

    public static Mono<ResponseEntity<Object>> realtimeTransportErrorResponse(WebClientRequestException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "status", HttpStatus.BAD_GATEWAY.value(),
                        "message", "No pude abrir la sesion de voz porque la conexion con OpenAI se corto. Reintenta."
                )));
    }

    public static Mono<ResponseEntity<Object>> badRequestResponse(IllegalArgumentException error) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "message", error.getMessage()
                )));
    }

    public static ResponseEntity<Object> promptLimitResponse(PromptLimitStatus promptLimit) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "message", "Agotaste tu demo gratuita para esta IP. Cada IP tiene "
                                + promptLimit.maxTokens()
                                + " tokens OpenAI: cada interaccion usa "
                                + promptLimit.chatTokenCost()
                                + " tokens y cada llamada de voz dura como maximo "
                                + promptLimit.voiceSessionSeconds()
                                + " segundos."
                ));
    }
}
