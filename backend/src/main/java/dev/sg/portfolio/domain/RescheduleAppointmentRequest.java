package dev.sg.portfolio.domain;

public record RescheduleAppointmentRequest(
        String sessionId,
        String startAt
) {
}
