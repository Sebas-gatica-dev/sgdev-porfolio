package dev.sg.portfolio.domain;

public record AvailabilitySearchRequest(
        String sessionId,
        String consultationType,
        String dateFrom,
        String dateTo,
        String preferredTimeFrom,
        String preferredTimeTo
) {
}
