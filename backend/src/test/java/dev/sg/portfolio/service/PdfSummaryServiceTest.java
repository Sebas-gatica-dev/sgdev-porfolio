package dev.sg.portfolio.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.sg.portfolio.config.OpenAiProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PdfSummaryServiceTest {

    private final OpenAiResponsesClient openAi = mock(OpenAiResponsesClient.class);
    private final PdfSummaryService service = new PdfSummaryService(openAi, properties());

    @Test
    void rejectsBodiesWithoutPdfSignature() {
        MockServerHttpRequest request = request("demo.pdf", "no soy pdf".getBytes(StandardCharsets.UTF_8));

        StepVerifier.create(service.summarize(request))
                .expectErrorMatches(error -> error instanceof PdfSummaryException pdfError
                        && pdfError.statusCode() == 400
                        && pdfError.getMessage().contains("no parece ser un PDF valido"))
                .verify();
    }

    @Test
    void summarizesValidatedPdfInMemory() {
        byte[] pdfBytes = "%PDF-1.4 demo".getBytes(StandardCharsets.US_ASCII);
        when(openAi.summarizePdf(any(byte[].class), eq("demo.pdf"))).thenReturn(Mono.just("resumen"));

        StepVerifier.create(service.summarize(request("demo.pdf", pdfBytes)))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals("demo.pdf", response.fileName());
                    org.junit.jupiter.api.Assertions.assertEquals(pdfBytes.length, response.sizeBytes());
                    org.junit.jupiter.api.Assertions.assertEquals("gpt-5-mini", response.model());
                    org.junit.jupiter.api.Assertions.assertTrue(response.ephemeral());
                    org.junit.jupiter.api.Assertions.assertEquals("resumen", response.summary());
                })
                .verifyComplete();
    }

    private MockServerHttpRequest request(String fileName, byte[] bytes) {
        return MockServerHttpRequest.post("/api/agent/document/summary")
                .contentType(MediaType.APPLICATION_PDF)
                .header("X-File-Name", fileName)
                .body(Mono.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)));
    }

    private OpenAiProperties properties() {
        return new OpenAiProperties(
                "key",
                "https://api.openai.com/v1",
                "gpt-5-mini",
                "gpt-5-mini",
                "gpt-4o-mini-transcribe",
                "es",
                "",
                "gpt-realtime-mini",
                "alloy",
                "",
                "https://api.openai.com/v1/realtime/calls"
        );
    }
}
