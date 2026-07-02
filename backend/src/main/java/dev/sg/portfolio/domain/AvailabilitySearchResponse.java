package dev.sg.portfolio.domain;

import java.util.List;

public record AvailabilitySearchResponse(
        String consultationType,
        String requestedSlotStatus,
        String requestedSlotReason,
        List<AppointmentSlotSuggestion> availableSlots
) {
}
