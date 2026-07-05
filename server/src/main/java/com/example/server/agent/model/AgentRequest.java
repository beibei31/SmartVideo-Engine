package com.example.server.agent.model;

public record AgentRequest(
        String sessionId,
        Long videoId,
        String question,
        String mode
) {
}
