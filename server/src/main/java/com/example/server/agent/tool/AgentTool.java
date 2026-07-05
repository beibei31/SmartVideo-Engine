package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;

public interface AgentTool<I, O> {

    String name();

    O execute(I input, AgentState state);
}
