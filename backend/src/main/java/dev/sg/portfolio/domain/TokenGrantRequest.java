package dev.sg.portfolio.domain;

public record TokenGrantRequest(
        String ipHash,
        String clientIp,
        int tokens,
        String note
) {
}
