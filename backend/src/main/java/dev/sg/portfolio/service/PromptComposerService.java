package dev.sg.portfolio.service;

import dev.sg.portfolio.domain.AgentRoute;
import dev.sg.portfolio.domain.DynamicContextItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PromptComposerService {

    private final PromptLibraryService promptLibraryService;

    public PromptComposerService(PromptLibraryService promptLibraryService) {
        this.promptLibraryService = promptLibraryService;
    }

    public PromptPlan compose(
            AgentRoute route,
            List<String> requestedExtensions,
            List<DynamicContextItem> dynamicContextItems
    ) {
        PromptLibraryService.PromptSource agentPrompt = promptLibraryService.agentPrompt(route.id());
        List<String> loadedExtensions = new ArrayList<>();
        List<String> missingExtensions = new ArrayList<>();
        StringBuilder extensionsBlock = new StringBuilder();

        for (String extensionName : requestedExtensions) {
            if (extensionName == null || extensionName.isBlank()) {
                continue;
            }

            try {
                PromptLibraryService.PromptSource extensionPrompt = promptLibraryService.extensionPrompt(extensionName);
                loadedExtensions.add(extensionPrompt.path());
                extensionsBlock.append("\n\n## Extension: ")
                        .append(extensionName.trim().toLowerCase())
                        .append("\n")
                        .append(extensionPrompt.content());
            } catch (IllegalArgumentException error) {
                missingExtensions.add(extensionName.trim().toLowerCase());
            }
        }

        String dynamicContextBlock = dynamicContextBlock(dynamicContextItems);
        String instructions = """
                %s

                ## Prompt Del Agente (%s)
                %s
                %s
                %s
                """.formatted(
                promptLibraryService.corePrompt(),
                route.id(),
                agentPrompt.content(),
                extensionsBlock,
                dynamicContextBlock
        ).trim();

        return new PromptPlan(
                instructions,
                agentPrompt.path(),
                loadedExtensions,
                missingExtensions,
                dynamicContextItems
        );
    }

    private String dynamicContextBlock(List<DynamicContextItem> items) {
        if (items.isEmpty()) {
            return "";
        }

        StringBuilder block = new StringBuilder("## Contexto Dinamico Del Turno");
        for (DynamicContextItem item : items) {
            block.append("\n- Fuente: ")
                    .append(item.name())
                    .append(" (")
                    .append(item.type())
                    .append(", ")
                    .append(item.success() ? "ok" : "error")
                    .append(")\n")
                    .append(item.content());
        }
        block.append("\n\nUsa este contexto solo si aporta valor para responder el mensaje actual.");
        return block.toString();
    }
}
