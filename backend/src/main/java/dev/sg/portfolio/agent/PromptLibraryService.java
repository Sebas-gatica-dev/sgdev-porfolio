package dev.sg.portfolio.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PromptLibraryService {

    private static final String CORE_PROMPT_PATH = "prompts/core/system.md";
    private static final String AGENT_PROMPT_ROOT = "prompts/agents/";
    private static final String EXTENSION_PROMPT_ROOT = "prompts/extensions/";
    private static final String REALTIME_ROOT = "prompts/realtime/";
    private static final String FALLBACK_AGENT = "coordinator";

    public String corePrompt() {
        return readRequired(CORE_PROMPT_PATH);
    }

    public PromptSource agentPrompt(String agentId) {
        String safeId = safeName(agentId).orElse(FALLBACK_AGENT);
        String directPath = AGENT_PROMPT_ROOT + safeId + ".md";
        Optional<String> direct = readOptional(directPath);
        if (direct.isPresent()) {
            return new PromptSource(directPath, direct.get());
        }

        String fallbackPath = AGENT_PROMPT_ROOT + FALLBACK_AGENT + ".md";
        return new PromptSource(fallbackPath, readRequired(fallbackPath));
    }

    public PromptSource extensionPrompt(String extensionName) {
        String safe = safeName(extensionName)
                .orElseThrow(() -> new IllegalArgumentException("Nombre de extension invalido."));

        String[] candidates = new String[] {
                EXTENSION_PROMPT_ROOT + safe + ".txt",
                EXTENSION_PROMPT_ROOT + safe + ".md"
        };
        for (String candidate : candidates) {
            Optional<String> value = readOptional(candidate);
            if (value.isPresent()) {
                return new PromptSource(candidate, value.get());
            }
        }
        throw new IllegalArgumentException("No existe prompts/extensions/" + safe + ".txt o .md");
    }

    public String conversationBasePrompt() {
        return readRequired(REALTIME_ROOT + "conversation/base.md");
    }

    public String appointmentScenarioPrompt(String consultationType) {
        String type = safeName(consultationType).orElse("traumatology");
        String path = REALTIME_ROOT + "appointments/" + type + ".md";
        return readOptional(path).orElseGet(() -> readRequired(REALTIME_ROOT + "appointments/traumatology.md"));
    }

    public String documentSummaryPrompt() {
        return readRequired(AGENT_PROMPT_ROOT + "document-summary.md");
    }

    public String documentSummaryTaskPrompt() {
        return readRequired(AGENT_PROMPT_ROOT + "document-summary-task.md");
    }

    private Optional<String> safeName(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String clean = value.trim().toLowerCase();
        if (!clean.matches("[a-z0-9._-]+")) {
            return Optional.empty();
        }
        return Optional.of(clean);
    }

    private String readRequired(String path) {
        return readOptional(path).orElseThrow(() -> new IllegalStateException("Falta prompt requerido: " + path));
    }

    private Optional<String> readOptional(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (var inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return Optional.of(new String(bytes, StandardCharsets.UTF_8).trim());
        } catch (IOException error) {
            throw new IllegalStateException("No pude leer prompt: " + path, error);
        }
    }

    public record PromptSource(String path, String content) {
    }
}
