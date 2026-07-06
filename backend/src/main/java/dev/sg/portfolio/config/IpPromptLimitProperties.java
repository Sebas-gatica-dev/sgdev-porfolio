package dev.sg.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio.ip-prompt-limit")
public record IpPromptLimitProperties(
        boolean enabled,
        int maxTokens,
        int chatTokenCost,
        int voiceTokenCost,
        int voiceSessionSeconds,
        int maxVoiceSeconds,
        String adminToken
) {
    public IpPromptLimitProperties {
        if (maxTokens <= 0) {
            maxTokens = 200;
        }
        if (chatTokenCost <= 0) {
            chatTokenCost = 10;
        }
        if (voiceTokenCost <= 0) {
            voiceTokenCost = 10;
        }
        if (voiceSessionSeconds <= 0) {
            voiceSessionSeconds = 120;
        }
        voiceSessionSeconds = Math.min(voiceSessionSeconds, 120);
        if (maxVoiceSeconds <= 0) {
            maxVoiceSeconds = 300;
        }
        adminToken = adminToken == null ? "" : adminToken.trim();
    }
}
