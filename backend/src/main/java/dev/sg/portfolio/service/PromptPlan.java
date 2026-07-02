package dev.sg.portfolio.service;

import dev.sg.portfolio.domain.DynamicContextItem;
import java.util.List;

public record PromptPlan(
        String instructions,
        String agentPromptPath,
        List<String> loadedExtensions,
        List<String> missingExtensions,
        List<DynamicContextItem> dynamicContextItems
) {
}
