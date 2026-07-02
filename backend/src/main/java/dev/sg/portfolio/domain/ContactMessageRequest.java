package dev.sg.portfolio.domain;

public record ContactMessageRequest(
        String name,
        String email,
        String company,
        String message
) {
}
