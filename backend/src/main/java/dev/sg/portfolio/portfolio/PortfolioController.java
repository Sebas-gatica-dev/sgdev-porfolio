package dev.sg.portfolio.portfolio;

import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.service.ClientIpResolver;
import dev.sg.portfolio.service.FreeModelClient;
import dev.sg.portfolio.service.OpenAiRealtimeClient;
import dev.sg.portfolio.service.OpenAiResponsesClient;
import dev.sg.portfolio.usage.IpPromptLimitService;
import java.util.List;
import java.util.Map;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final OpenAiResponsesClient openAi;
    private final FreeModelClient freeModel;
    private final OpenAiRealtimeClient realtime;
    private final ClientIpResolver clientIpResolver;
    private final IpPromptLimitService promptLimitService;

    public PortfolioController(
            OpenAiResponsesClient openAi,
            FreeModelClient freeModel,
            OpenAiRealtimeClient realtime,
            ClientIpResolver clientIpResolver,
            IpPromptLimitService promptLimitService
    ) {
        this.openAi = openAi;
        this.freeModel = freeModel;
        this.realtime = realtime;
        this.clientIpResolver = clientIpResolver;
        this.promptLimitService = promptLimitService;
    }

    @GetMapping("/health")
    public Map<String, Object> health(ServerHttpRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        PromptLimitStatus promptAvailability = promptLimitService.status(clientIp);
        PromptLimitStatus voiceAvailability = promptLimitService.voiceMinuteStatus(clientIp);

        return Map.ofEntries(
                Map.entry("ok", true),
                Map.entry("mode", openAi.configured() ? "portfolio-runtime-live" : "portfolio-runtime-local"),
                Map.entry("openaiConfigured", openAi.configured()),
                Map.entry("freeModelConfigured", freeModel.configured()),
                Map.entry("freeModelName", freeModel.model()),
                Map.entry("voiceConfigured", realtime.configured()),
                Map.entry("promptLimitEnabled", promptLimitService.enabled()),
                Map.entry("promptLimitUsed", promptAvailability.used()),
                Map.entry("promptLimitRemaining", promptAvailability.remaining()),
                Map.entry("promptLimitMaxTokens", promptAvailability.maxTokens()),
                Map.entry("promptLimitMaxPrompts", promptAvailability.maxTokens()),
                Map.entry("promptLimitChatTokenCost", promptAvailability.chatTokenCost()),
                Map.entry("promptLimitNewVisitor", promptAvailability.newVisitor()),
                Map.entry("promptLimitTokenRequestPending", promptAvailability.tokenRequestPending()),
                Map.entry("openaiPromptAvailable", promptAvailability.allowed()),
                Map.entry("openaiVoiceAvailable", realtime.configured() && voiceAvailability.allowed()),
                Map.entry("openaiVoiceCreditCost", promptLimitService.voiceMinuteCost()),
                Map.entry("openaiVoiceTokenCost", promptLimitService.voiceMinuteCost()),
                Map.entry("openaiVoiceSessionSeconds", promptLimitService.voiceSessionSeconds()),
                Map.entry("openaiVoiceSecondsUsed", voiceAvailability.voiceSecondsUsed()),
                Map.entry("openaiVoiceSecondsRemaining", voiceAvailability.voiceSecondsRemaining()),
                Map.entry("openaiVoiceMaxSeconds", voiceAvailability.maxVoiceSeconds()),
                Map.entry("runtime", "Portfolio assistant + demo tools + prompt composer + voice")
        );
    }

    @GetMapping("/blueprint")
    public Map<String, Object> blueprint() {
        return Map.of(
                "name", "SG AI Agent Portfolio",
                "backend", "Spring Boot WebFlux",
                "runtime", "Portfolio assistant architecture",
                "llm", "OpenAI Responses API + Realtime API",
                "architecture", List.of(
                        "coordinator router",
                        "specialist agents",
                        "function tools",
                        "dynamic context",
                        "prompt library",
                        "human confirmation gates",
                        "streaming traces"
                ),
                "upstream", "standalone portfolio runtime"
        );
    }
}
