package dev.sg.portfolio.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.sg.portfolio.domain.AssistantChatRequest;
import dev.sg.portfolio.domain.TextChunk;
import dev.sg.portfolio.service.FreeModelClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AssistantControllerTest {

    @Test
    void streamsOnlyThroughConfiguredRagClient() {
        FreeModelClient client = mock(FreeModelClient.class);
        when(client.configured()).thenReturn(true);
        when(client.streamText(eq("Como funciona?"), anyString(), eq("session-1")))
                .thenReturn(Flux.just("Respuesta ", "RAG"));
        AssistantController controller = new AssistantController(client, new PromptLibraryService());

        StepVerifier.create(controller.stream(new AssistantChatRequest("Como funciona?", "session-1", Map.of())))
                .assertNext(event -> {
                    assertEquals("chunk", event.event());
                    assertEquals("Respuesta ", ((TextChunk) event.data()).text());
                })
                .assertNext(event -> assertEquals("chunk", event.event()))
                .assertNext(event -> assertEquals("done", event.event()))
                .verifyComplete();
    }

    @Test
    void rejectsBlankPromptWithoutCallingRuntime() {
        FreeModelClient client = mock(FreeModelClient.class);
        AssistantController controller = new AssistantController(client, new PromptLibraryService());

        StepVerifier.create(controller.stream(new AssistantChatRequest("  ", "", Map.of())))
                .assertNext(event -> assertEquals("error", event.event()))
                .verifyComplete();
    }
}
