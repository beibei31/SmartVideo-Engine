package com.example.server.agent.model;

public record VideoSegmentLocatorInput(
        String query,
        Long videoId,
        Integer topK
) {
}
