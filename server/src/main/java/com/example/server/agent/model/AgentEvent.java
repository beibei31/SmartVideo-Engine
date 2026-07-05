package com.example.server.agent.model;

public record AgentEvent(
        String event,
        Object data
) {
}
