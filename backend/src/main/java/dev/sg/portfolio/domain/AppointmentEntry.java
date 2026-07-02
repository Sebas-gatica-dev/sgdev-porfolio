package dev.sg.portfolio.domain;

public record AppointmentEntry(
        String id,
        String doctorId,
        String consultationType,
        String doctorName,
        String specialty,
        String patientName,
        String startAt,
        String endAt,
        boolean fromCurrentSession
) {
}
