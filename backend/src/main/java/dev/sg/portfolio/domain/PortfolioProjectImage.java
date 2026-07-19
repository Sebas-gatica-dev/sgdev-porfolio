package dev.sg.portfolio.domain;

import java.time.LocalDateTime;

public record PortfolioProjectImage(
        String id,
        String projectId,
        String kind,
        String storageKey,
        String url,
        String altText,
        int sortOrder,
        String mimeType,
        long sizeBytes,
        Integer width,
        Integer height,
        LocalDateTime createdAt
) {
}
