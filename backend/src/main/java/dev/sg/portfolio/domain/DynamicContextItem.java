package dev.sg.portfolio.domain;

public record DynamicContextItem(
        String name,
        String type,
        boolean success,
        String content
) {
}
