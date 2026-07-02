package dev.sg.portfolio.domain;

public record VoiceSessionResponse(
        String clientSecret,
        long expiresAt,
        String model,
        String realtimeUrl
) {
}
