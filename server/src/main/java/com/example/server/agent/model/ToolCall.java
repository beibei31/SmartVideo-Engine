package com.example.server.agent.model;

import java.util.Map;

public record ToolCall(
        String thought,
        String action,
        Map<String, Object> arguments
) {
}
