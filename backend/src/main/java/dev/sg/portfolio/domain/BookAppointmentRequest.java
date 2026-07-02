package dev.sg.portfolio.domain;

public record BookAppointmentRequest(
        String sessionId,
        String consultationType,
        String patientName,
        String startAt
) {
}
