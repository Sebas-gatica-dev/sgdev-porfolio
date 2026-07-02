package dev.sg.portfolio.domain;

import java.util.List;

public record ChatStreamRequest(
        String message,
        String sessionId,
        String agentId,
        List<String> extensions,
        List<DynamicContextRequest> dynamicContext
) {
}
