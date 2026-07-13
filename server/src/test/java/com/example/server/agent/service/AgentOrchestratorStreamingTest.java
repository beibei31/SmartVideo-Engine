package com.example.server.agent.service;

import com.example.server.agent.model.AgentEvent;
import com.example.server.agent.model.AgentRequest;
import com.example.server.agent.model.KnowledgeQaResult;
import com.example.server.agent.model.ToolCall;
import com.example.server.agent.model.ToolResult;
import com.example.server.rag.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void streamingRunEmitsKnowledgeContextsBeforeFinalAnswer() {
        PlannerService plannerService = mock(PlannerService.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(plannerService, toolExecutor, 5);
        AgentRequest request = new AgentRequest("session-1", 42L, "Redis 持久化讲了什么", null);
        ToolCall call = new ToolCall("检索视频知识库", "KnowledgeQaTool", Map.of("question", "Redis 持久化讲了什么"));
        SearchResult context = SearchResult.builder()
                .chunkId("video-42-001")
                .content("Redis 持久化包含 RDB 和 AOF。")
                .score(0.87)
                .sourceTitle("redis-interview.mp4")
                .chunkIndex(1)
                .retrievalType("HYBRID")
                .build();

        when(plannerService.plan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("Redis 持久化讲了什么")))
                .thenReturn(call);
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ToolResult.success("KnowledgeQaTool", new KnowledgeQaResult("主要讲了 RDB 和 AOF。", List.of(context))));

        List<AgentEvent> events = new ArrayList<>();
        orchestrator.runStreaming(request, events::add);

        assertEquals("contexts", events.get(3).event());
        assertSame(context, ((List<?>) events.get(3).data()).get(0));
        assertEquals("final_answer", events.get(4).event());
        assertEquals("主要讲了 RDB 和 AOF。", events.get(4).data());
        assertEquals("done", events.get(5).event());
    }
}
