package com.example.server.agent.model;

import lombok.Builder;

import java.util.List;

@Builder
public record AgentState(
        String sessionId,
        Long currentVideoId,
        String currentTopic,
        List<String> history,
        List<AgentStep> steps
) {

    public AgentState {
        history = history == null ? List.of() : List.copyOf(history);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
