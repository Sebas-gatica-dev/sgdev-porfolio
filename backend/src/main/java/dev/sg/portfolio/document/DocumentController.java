package dev.sg.portfolio.document;

import static dev.sg.portfolio.shared.web.ApiResponses.badGatewayResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.okResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.pdfSummaryErrorResponse;
import static dev.sg.portfolio.shared.web.ApiResponses.promptLimitResponse;

import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.service.ClientIpResolver;
import dev.sg.portfolio.usage.IpPromptLimitService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agent/document")
public class DocumentController {

    private final ClientIpResolver clientIpResolver;
    private final IpPromptLimitService promptLimitService;
    private final PdfSummaryService pdfSummaryService;

    public DocumentController(
            ClientIpResolver clientIpResolver,
            IpPromptLimitService promptLimitService,
            PdfSummaryService pdfSummaryService
    ) {
        this.clientIpResolver = clientIpResolver;
        this.promptLimitService = promptLimitService;
        this.pdfSummaryService = pdfSummaryService;
    }

    @PostMapping(
            value = "/summary",
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
                .flatMap(response -> okResponse(response))
                .onErrorResume(PdfSummaryException.class, error -> pdfSummaryErrorResponse(error))
                .onErrorResume(IllegalStateException.class, error -> badGatewayResponse(error));
    }
}
