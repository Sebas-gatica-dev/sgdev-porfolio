package dev.sg.portfolio.domain;

public record DocumentSummaryResponse(
        String fileName,
        int sizeBytes,
        int maxSizeBytes,
        String model,
        boolean ephemeral,
        String summary
) {
}
