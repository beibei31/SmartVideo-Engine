package com.example.server.agent.model;

public record VideoSearchInput(
        String query,
        Long videoId,
        Integer topK
) {
}
