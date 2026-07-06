package dev.sg.portfolio.domain;

public record AppointmentChatRequest(
        String message,
        String sessionId,
        String consultationType
) {
}
