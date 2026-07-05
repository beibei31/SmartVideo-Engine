package com.example.server.agent.model;

import java.util.List;

public record AgentResponse(
        String sessionId,
        Long videoId,
        String question,
        String answer,
        List<AgentStep> steps
) {
}
