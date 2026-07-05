package com.example.server.agent.service;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.ToolCall;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerServiceTest {

    @Test
    void parsesPlannerJsonIntoToolCall() {
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        PlannerService plannerService = new PlannerService(chatLanguageModel);
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.contains("Redis 缓存击穿")))
                .thenReturn("""
                        {
                          "thought": "需要先检索视频片段",
                          "action": "VideoSearchTool",
                          "arguments": {
                            "query": "Redis 缓存击穿",
                            "topK": 5
                          }
                        }
                        """);

        ToolCall call = plannerService.plan(AgentState.builder()
                .sessionId("session-1")
                .currentVideoId(42L)
                .history(List.of())
                .build(), "Redis 缓存击穿");

        assertEquals("需要先检索视频片段", call.thought());
        assertEquals("VideoSearchTool", call.action());
        assertEquals("Redis 缓存击穿", call.arguments().get("query"));
        assertEquals(5, call.arguments().get("topK"));
    }

    @Test
    void returnsKnowledgeQaFallbackWhenPlannerJsonIsInvalid() {
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        PlannerService plannerService = new PlannerService(chatLanguageModel);
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("我觉得应该先回答问题");

        ToolCall call = plannerService.plan(AgentState.builder().currentVideoId(42L).build(), "Redis 是什么");

        assertEquals("KnowledgeQaTool", call.action());
        assertEquals("Redis 是什么", call.arguments().get("question"));
        assertEquals(42L, call.arguments().get("videoId"));
    }

    @Test
    void plannerPromptListsSupportedTools() {
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        PlannerService plannerService = new PlannerService(chatLanguageModel);
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {
                          "thought": "定位片段",
                          "action": "VideoSegmentLocatorTool",
                          "arguments": {"query": "RocketMQ", "topK": 3}
                        }
                        """);

        plannerService.plan(AgentState.builder().currentVideoId(42L).build(), "哪里讲 RocketMQ");

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(chatLanguageModel).chat(captor.capture());
        assertEquals(true, captor.getValue().contains("VideoSegmentLocatorTool"));
        assertEquals(true, captor.getValue().contains("VideoSummaryTool"));
        assertEquals(true, captor.getValue().contains("QuizTool"));
    }

    @Test
    void parsesJsonWrappedInMarkdownCodeFence() {
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        PlannerService plannerService = new PlannerService(chatLanguageModel);
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        ```json
                        {
                          "thought": "locate segment",
                          "action": "VideoSegmentLocatorTool",
                          "arguments": {"query": "Redis", "topK": 3}
                        }
                        ```
                        """);

        ToolCall call = plannerService.plan(AgentState.builder().currentVideoId(42L).build(), "where Redis");

        assertEquals("VideoSegmentLocatorTool", call.action());
        assertEquals("Redis", call.arguments().get("query"));
        assertEquals(3, call.arguments().get("topK"));
    }

    @Test
    void rejectsActionOutsideWhitelist() {
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        PlannerService plannerService = new PlannerService(chatLanguageModel);
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {
                          "thought": "unsafe",
                          "action": "DeleteEverythingTool",
                          "arguments": {}
                        }
                        """);

        ToolCall call = plannerService.plan(AgentState.builder().currentVideoId(42L).build(), "do unsafe thing");

        assertEquals("KnowledgeQaTool", call.action());
        assertEquals("do unsafe thing", call.arguments().get("question"));
    }

    @Test
    void fillsDefaultArgumentsForKnownActions() {
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        PlannerService plannerService = new PlannerService(chatLanguageModel);
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {
                          "thought": "summary",
                          "action": "VideoSummaryTool"
                        }
                        """);

        ToolCall call = plannerService.plan(AgentState.builder().currentVideoId(42L).build(), "summarize Redis");

        assertEquals("VideoSummaryTool", call.action());
        assertEquals(42L, call.arguments().get("videoId"));
        assertEquals("summarize Redis", call.arguments().get("topic"));
        assertEquals("outline", call.arguments().get("summaryType"));
    }

    @Test
    void fillsDefaultArgumentsForQuizTool() {
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        PlannerService plannerService = new PlannerService(chatLanguageModel);
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {
                          "thought": "quiz",
                          "action": "QuizTool",
                          "arguments": {}
                        }
                        """);

        ToolCall call = plannerService.plan(AgentState.builder().currentVideoId(42L).build(), "考我 Redis");

        assertEquals("QuizTool", call.action());
        assertEquals(42L, call.arguments().get("videoId"));
        assertEquals("考我 Redis", call.arguments().get("topic"));
        assertEquals("medium", call.arguments().get("difficulty"));
        assertEquals(5, call.arguments().get("count"));
    }
}
