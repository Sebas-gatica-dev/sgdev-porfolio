package dev.sg.portfolio.domain;

public record AppointmentDoctor(
        String id,
        String consultationType,
        String name,
        String specialty
) {
}
