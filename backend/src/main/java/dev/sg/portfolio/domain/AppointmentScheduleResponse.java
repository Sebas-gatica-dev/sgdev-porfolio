package dev.sg.portfolio.domain;

import java.util.List;

public record AppointmentScheduleResponse(
        List<AppointmentDoctor> doctors,
        List<AppointmentCalendarDay> days,
        String workdayStart,
        String lunchStart,
        String lunchEnd,
        String workdayEnd
) {
}
