package dev.sg.portfolio.domain;

public record AppointmentSlotSuggestion(
        String doctorId,
        String doctorName,
        String specialty,
        String startAt,
        String endAt
) {
}
