package dev.sg.portfolio.domain;

import java.util.Map;

public record DynamicContextRequest(
        String type,
        String name,
        String url,
        Map<String, String> params,
        Integer timeoutMs
) {
}
