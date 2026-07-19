package dev.sg.portfolio.domain;

import java.util.List;

public record PortfolioProjectUpsertRequest(
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
        int sortOrder
) {
}
