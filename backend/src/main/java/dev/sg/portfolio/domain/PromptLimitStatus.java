package dev.sg.portfolio.domain;

public record PromptLimitStatus(
        boolean enabled,
        boolean allowed,
        int used,
        int remaining,
        int maxTokens,
        int voiceSecondsUsed,
        int voiceSecondsRemaining,
        int maxVoiceSeconds,
        int chatTokenCost,
        int voiceTokenCost,
        int voiceSessionSeconds,
        boolean newVisitor,
        boolean tokenRequestPending
) {
    public static PromptLimitStatus disabled(
            int maxTokens,
            int maxVoiceSeconds,
            int chatTokenCost,
            int voiceTokenCost,
            int voiceSessionSeconds
    ) {
        return new PromptLimitStatus(
                false,
                true,
                0,
                maxTokens,
                maxTokens,
                0,
                maxVoiceSeconds,
                maxVoiceSeconds,
                chatTokenCost,
                voiceTokenCost,
                voiceSessionSeconds,
                false,
                false
        );
    }

    public int maxPrompts() {
        return maxTokens;
    }
}
