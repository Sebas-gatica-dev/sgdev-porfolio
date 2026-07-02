package dev.sg.portfolio.domain;

public record PromptLimitStatus(
        boolean enabled,
        boolean allowed,
        int used,
        int remaining,
        int maxPrompts
) {
    public static PromptLimitStatus disabled(int maxPrompts) {
        return new PromptLimitStatus(false, true, 0, maxPrompts, maxPrompts);
    }
}
