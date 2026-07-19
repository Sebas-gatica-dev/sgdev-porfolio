package dev.sg.portfolio.domain;

import java.time.LocalDateTime;
import java.util.List;

public record PortfolioProject(
        String id,
        String slug,
        String title,
        String summary,
        String description,
        String liveUrl,
        String repositoryUrl,
        String infraAppSlug,
        List<String> techStack,
        String status,
        boolean featured,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime publishedAt,
        List<PortfolioProjectImage> images
) {
}
