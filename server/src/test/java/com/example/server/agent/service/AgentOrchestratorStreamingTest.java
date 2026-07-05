package com.example.server.agent.service;

import com.example.server.agent.model.AgentEvent;
import com.example.server.agent.model.AgentRequest;
import com.example.server.agent.model.KnowledgeQaResult;
import com.example.server.agent.model.ToolCall;
import com.example.server.agent.model.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorStreamingTest {

    @Test
    void streamingRunEmitsTraceEventsAndFinalAnswer() {
        PlannerService plannerService = mock(PlannerService.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(plannerService, toolExecutor, 5);
        AgentRequest request = new AgentRequest("session-1", 42L, "Redis 是什么", null);
        ToolCall call = new ToolCall("直接问答", "KnowledgeQaTool", Map.of("question", "Redis 是什么"));

        when(plannerService.plan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("Redis 是什么")))
                .thenReturn(call);
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ToolResult.success("KnowledgeQaTool", new KnowledgeQaResult("Redis 是内存数据库", List.of())));

        List<AgentEvent> events = new ArrayList<>();
        orchestrator.runStreaming(request, events::add);

        assertEquals("agent_status", events.get(0).event());
        assertEquals("tool_call", events.get(1).event());
        assertEquals("tool_result", events.get(2).event());
        assertEquals("final_answer", events.get(3).event());
        assertEquals("done", events.get(4).event());
        assertEquals("Redis 是内存数据库", events.get(3).data());
    }
}
