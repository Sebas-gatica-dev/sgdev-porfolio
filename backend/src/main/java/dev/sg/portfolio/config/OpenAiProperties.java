package dev.sg.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String documentModel,
        String voiceModel,
        String voiceLanguage,
        String voicePrompt,
        String conversationModel,
        String conversationVoice,
        String conversationInstructions,
        String realtimeWebrtcUrl
) {
    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }
}
