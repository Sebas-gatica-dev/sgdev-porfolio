package dev.sg.portfolio.domain;

public record ContactMessageResponse(
        String status,
        String message,
        boolean mailtrapReady
) {
}
