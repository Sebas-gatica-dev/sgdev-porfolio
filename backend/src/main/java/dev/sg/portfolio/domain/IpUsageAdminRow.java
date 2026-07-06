package dev.sg.portfolio.domain;

import java.time.LocalDateTime;

public record IpUsageAdminRow(
        String ipHash,
        String clientIp,
        int tokensUsed,
        int tokensRemaining,
        int tokenLimit,
        int voiceSecondsUsed,
        int voiceSecondsRemaining,
        int maxVoiceSeconds,
        int tokenRequestCount,
        String requestStatus,
        String adminNote,
        LocalDateTime firstSeenAt,
        LocalDateTime updatedAt,
        LocalDateTime lastTokenRequestAt,
        LocalDateTime lastGrantedAt
) {
}
