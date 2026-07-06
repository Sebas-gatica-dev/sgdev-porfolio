package dev.sg.portfolio.shared.web;

import dev.sg.portfolio.domain.TextChunk;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

public final class SseSupport {

    private SseSupport() {
    }

    public static ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    public static Flux<ServerSentEvent<Object>> textChunks(String answer) {
        return Flux.fromIterable(chunkText(answer == null ? "" : answer, 150))
                .map(text -> event("chunk", new TextChunk(text)));
    }

    private static List<String> chunkText(String value, int size) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return List.of("");
        }
        for (int index = 0; index < value.length(); index += size) {
            chunks.add(value.substring(index, Math.min(index + size, value.length())));
        }
        return chunks;
    }
}
