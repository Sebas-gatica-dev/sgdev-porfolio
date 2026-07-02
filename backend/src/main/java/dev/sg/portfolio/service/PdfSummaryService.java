package dev.sg.portfolio.service;

import dev.sg.portfolio.config.OpenAiProperties;
import dev.sg.portfolio.domain.DocumentSummaryResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
public class PdfSummaryService {

    public static final int MAX_PDF_BYTES = 10 * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final OpenAiResponsesClient openAi;
    private final OpenAiProperties properties;

    public PdfSummaryService(OpenAiResponsesClient openAi, OpenAiProperties properties) {
        this.openAi = openAi;
        this.properties = properties;
    }

    public Mono<DocumentSummaryResponse> summarize(ServerHttpRequest request) {
        validateContentType(request.getHeaders());
        String fileName = sanitizeFileName(request.getHeaders().getFirst("X-File-Name"));

        return DataBufferUtils.join(request.getBody(), MAX_PDF_BYTES)
                .onErrorMap(
                        DataBufferLimitException.class,
                        ignored -> new PdfSummaryException(
                                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                                "El PDF supera el limite de 10 MB."
                        )
                )
                .flatMap(buffer -> summarizeBuffer(buffer, fileName));
    }

    private Mono<DocumentSummaryResponse> summarizeBuffer(DataBuffer buffer, String fileName) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);

        validatePdfBytes(bytes);
        return openAi.summarizePdf(bytes, fileName)
                .map(summary -> new DocumentSummaryResponse(
                        fileName,
                        bytes.length,
                        MAX_PDF_BYTES,
                        documentModel(),
                        true,
                        summary
                ));
    }

    private void validateContentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null || !MediaType.APPLICATION_PDF.isCompatibleWith(contentType)) {
            throw new PdfSummaryException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                    "Solo se aceptan archivos PDF."
            );
        }
    }

    private void validatePdfBytes(byte[] bytes) {
        if (bytes.length == 0) {
            throw new PdfSummaryException(HttpStatus.BAD_REQUEST.value(), "El PDF esta vacio.");
        }

        if (bytes.length > MAX_PDF_BYTES) {
            throw new PdfSummaryException(
                    HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "El PDF supera el limite de 10 MB."
            );
        }

        if (bytes.length < PDF_SIGNATURE.length) {
            throw new PdfSummaryException(HttpStatus.BAD_REQUEST.value(), "El archivo no parece ser un PDF valido.");
        }

        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) {
                throw new PdfSummaryException(HttpStatus.BAD_REQUEST.value(), "El archivo no parece ser un PDF valido.");
            }
        }
    }

    private String sanitizeFileName(String rawFileName) {
        if (!StringUtils.hasText(rawFileName)) {
            return "documento.pdf";
        }

        String decoded = URLDecoder.decode(rawFileName.trim(), StandardCharsets.UTF_8);
        String cleaned = decoded.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!cleaned.toLowerCase().endsWith(".pdf")) {
            return cleaned + ".pdf";
        }

        return cleaned;
    }

    private String documentModel() {
        return StringUtils.hasText(properties.documentModel())
                ? properties.documentModel()
                : properties.model();
    }
}
