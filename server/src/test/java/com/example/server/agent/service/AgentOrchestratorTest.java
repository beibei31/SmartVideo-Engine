package com.example.server.agent.service;

import com.example.server.agent.model.AgentRequest;
import com.example.server.agent.model.AgentResponse;
import com.example.server.agent.model.KnowledgeQaResult;
import com.example.server.agent.model.QuizResult;
import com.example.server.agent.model.ToolCall;
import com.example.server.agent.model.ToolResult;
import com.example.server.agent.model.VideoSummaryResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    @Test
    void stopsWhenPlannerReturnsFinalAnswer() {
        PlannerService plannerService = mock(PlannerService.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(plannerService, toolExecutor, 5);
        AgentRequest request = new AgentRequest("session-1", 42L, "总结 Redis", null);

        when(plannerService.plan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("总结 Redis")))
                .thenReturn(new ToolCall("可以直接回答", "FinalAnswer", Map.of("answer", "Redis 总结")));

        AgentResponse response = orchestrator.run(request);

        assertEquals("Redis 总结", response.answer());
        assertEquals(0, response.steps().size());
    }

    @Test
    void stopsAtMaxStepsWhenPlannerNeverFinalizes() {
        PlannerService plannerService = mock(PlannerService.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(plannerService, toolExecutor, 2);
        AgentRequest request = new AgentRequest("session-1", 42L, "找 Redis", null);
        ToolCall call = new ToolCall("继续检索", "VideoSearchTool", Map.of("query", "Redis"));

        when(plannerService.plan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("找 Redis")))
                .thenReturn(call);
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ToolResult.success("VideoSearchTool", "ok"));

        AgentResponse response = orchestrator.run(request);

        assertTrue(response.answer().contains("已达到最大执行步数"));
        assertEquals(2, response.steps().size());
    }

    @Test
    void returnsImmediatelyWhenKnowledgeQaToolProducesAnswer() {
        PlannerService plannerService = mock(PlannerService.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(plannerService, toolExecutor, 5);
        AgentRequest request = new AgentRequest("session-1", 42L, "Redis 是什么", null);
        ToolCall call = new ToolCall("直接问答", "KnowledgeQaTool", Map.of("question", "Redis 是什么"));

        when(plannerService.plan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("Redis 是什么")))
                .thenReturn(call);
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ToolResult.success("KnowledgeQaTool", new KnowledgeQaResult("Redis 是内存数据库", java.util.List.of())));

        AgentResponse response = orchestrator.run(request);

        assertEquals("Redis 是内存数据库", response.answer());
        assertEquals(1, response.steps().size());
    }

    @Test
    void returnsImmediatelyWhenSummaryToolProducesSummary() {
        PlannerService plannerService = mock(PlannerService.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(plannerService, toolExecutor, 5);
        AgentRequest request = new AgentRequest("session-1", 42L, "总结 Redis", null);
        ToolCall call = new ToolCall("生成总结", "VideoSummaryTool", Map.of("topic", "Redis"));

        when(plannerService.plan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("总结 Redis")))
                .thenReturn(call);
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ToolResult.success("VideoSummaryTool", new VideoSummaryResult("interview", "Redis 面试总结", java.util.List.of())));

        AgentResponse response = orchestrator.run(request);

        assertEquals("Redis 面试总结", response.answer());
        assertEquals(1, response.steps().size());
    }

    @Test
    void returnsImmediatelyWhenQuizToolProducesQuiz() {
        PlannerService plannerService = mock(PlannerService.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(plannerService, toolExecutor, 5);
        AgentRequest request = new AgentRequest("session-1", 42L, "考我 Redis", null);
        ToolCall call = new ToolCall("生成测验", "QuizTool", Map.of("topic", "Redis"));

        when(plannerService.plan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("考我 Redis")))
                .thenReturn(call);
        when(toolExecutor.execute(org.mockito.ArgumentMatchers.eq(call), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ToolResult.success("QuizTool", new QuizResult("Redis", "medium", 5, "1. Redis 是什么？", java.util.List.of())));

        AgentResponse response = orchestrator.run(request);

        assertEquals("1. Redis 是什么？", response.answer());
        assertEquals(1, response.steps().size());
    }
}
