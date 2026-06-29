package com.example.server.rag.model;

import lombok.Builder;

import java.util.List;

@Builder
public record RetrievalRequest(
        String question,
        Long videoId,
        boolean hybrid,
        List<String> history,
        Integer topK
) {

    public RetrievalRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
