package dev.sg.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio.ip-prompt-limit")
public record IpPromptLimitProperties(
        boolean enabled,
        int maxPrompts,
        int chatCost,
        int voiceMinuteCost,
        int voiceSessionSeconds
) {
    public IpPromptLimitProperties {
        if (maxPrompts <= 0) {
            maxPrompts = 20;
        }
        if (chatCost <= 0) {
            chatCost = 1;
        }
        if (voiceMinuteCost <= 0) {
            voiceMinuteCost = 5;
        }
        if (voiceSessionSeconds <= 0) {
            voiceSessionSeconds = 60;
        }
        voiceSessionSeconds = Math.min(voiceSessionSeconds, 60);
    }
}
