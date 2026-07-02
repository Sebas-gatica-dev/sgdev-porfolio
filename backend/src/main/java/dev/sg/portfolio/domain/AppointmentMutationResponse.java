package dev.sg.portfolio.domain;

public record AppointmentMutationResponse(
        String status,
        AppointmentEntry appointment
) {
}
