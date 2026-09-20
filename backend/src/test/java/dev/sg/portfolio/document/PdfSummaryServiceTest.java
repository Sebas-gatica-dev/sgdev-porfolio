package dev.sg.portfolio.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.sg.portfolio.config.OpenAiProperties;
import dev.sg.portfolio.service.FreeModelClient;
import reactor.core.publisher.Flux;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PdfSummaryServiceTest {

    private final FreeModelClient freeModel = mock(FreeModelClient.class);
    private final PdfSummaryService service = new PdfSummaryService(freeModel);

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
    void summarizesValidatedPdfInMemory() throws Exception {
        byte[] pdfBytes;
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage(); doc.addPage(page);
            try (var content = new PDPageContentStream(doc, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.showText("Proyecto de prueba. Entrega el lunes."); content.endText();
            }
            doc.save(out); pdfBytes = out.toByteArray();
        }
        when(freeModel.streamText(any(), any(), eq(""))).thenReturn(Flux.just("resumen"));
        when(freeModel.model()).thenReturn("qwen3:0.6b");

        StepVerifier.create(service.summarize(request("demo.pdf", pdfBytes)))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals("demo.pdf", response.fileName());
                    org.junit.jupiter.api.Assertions.assertEquals(pdfBytes.length, response.sizeBytes());
                    org.junit.jupiter.api.Assertions.assertEquals("qwen3:0.6b", response.model());
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
