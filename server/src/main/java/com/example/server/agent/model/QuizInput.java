package com.example.server.agent.model;

public record QuizInput(
        Long videoId,
        String topic,
        String difficulty,
        Integer count
) {
}
