package dev.sg.portfolio.domain;

public record PortfolioProjectImageUploadRequest(
        String fileName,
        String contentType,
        String dataBase64,
        String kind,
        String altText,
        int sortOrder
) {
}
