package com.example.server.agent.model;

public record ToolResult(
        String toolName,
        boolean success,
        Object data,
        String error
) {

    public static ToolResult success(String toolName, Object data) {
        return new ToolResult(toolName, true, data, null);
    }

    public static ToolResult failure(String toolName, String error) {
        return new ToolResult(toolName, false, null, error);
    }
}
