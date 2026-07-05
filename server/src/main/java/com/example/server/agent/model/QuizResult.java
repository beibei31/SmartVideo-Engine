package com.example.server.agent.model;

import java.util.List;

public record QuizResult(
        String topic,
        String difficulty,
        int count,
        String quiz,
        List<VideoSegment> references
) {
}
