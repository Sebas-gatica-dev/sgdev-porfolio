package dev.sg.portfolio.domain;

import java.util.List;

public record AppointmentCalendarDay(
        String date,
        boolean workingDay,
        List<AppointmentEntry> appointments
) {
}
