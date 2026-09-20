package dev.sg.portfolio.domain;

import java.util.Map;

public record AssistantChatRequest(
        String prompt,
        String sessionId,
        Map<String, Object> metadata
) {
}
