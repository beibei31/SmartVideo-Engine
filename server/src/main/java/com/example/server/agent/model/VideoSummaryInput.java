package com.example.server.agent.model;

public record VideoSummaryInput(
        Long videoId,
        String topic,
        String summaryType
) {
}
