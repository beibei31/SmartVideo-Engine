package com.example.server.agent.model;

public record AgentStep(
        ToolCall toolCall,
        ToolResult toolResult
) {
}
