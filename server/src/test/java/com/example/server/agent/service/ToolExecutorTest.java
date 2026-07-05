package com.example.server.agent.service;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.ToolCall;
import com.example.server.agent.model.ToolResult;
import com.example.server.agent.model.VideoSearchResult;
import com.example.server.agent.model.VideoSegment;
import com.example.server.agent.tool.KnowledgeQaTool;
import com.example.server.agent.tool.QuizTool;
import com.example.server.agent.tool.VideoSegmentLocatorTool;
import com.example.server.agent.tool.VideoSearchTool;
import com.example.server.agent.tool.VideoSummaryTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutorTest {

    @Test
    void executesVideoSearchToolFromPlannerArguments() {
        VideoSearchTool videoSearchTool = mock(VideoSearchTool.class);
        KnowledgeQaTool knowledgeQaTool = mock(KnowledgeQaTool.class);
        ToolExecutor executor = new ToolExecutor(
                videoSearchTool,
                knowledgeQaTool,
                mock(VideoSegmentLocatorTool.class),
                mock(VideoSummaryTool.class),
                mock(QuizTool.class)
        );
        AgentState state = AgentState.builder().currentVideoId(42L).build();
        VideoSearchResult expected = new VideoSearchResult(List.of(
                new VideoSegment(42L, 10L, 20L, "Redis", 0.9, "demo.mp4", "chunk-1")
        ));
        when(videoSearchTool.name()).thenReturn("VideoSearchTool");
        when(videoSearchTool.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(state)))
                .thenReturn(expected);

        ToolResult result = executor.execute(new ToolCall(
                "需要先检索视频片段",
                "VideoSearchTool",
                Map.of("query", "Redis", "videoId", 42, "topK", 5)
        ), state);

        assertTrue(result.success());
        assertEquals("VideoSearchTool", result.toolName());
        assertEquals(expected, result.data());
        verify(videoSearchTool).execute(org.mockito.ArgumentMatchers.argThat(input ->
                input.query().equals("Redis") && input.videoId().equals(42L) && input.topK().equals(5)
        ), org.mockito.ArgumentMatchers.eq(state));
    }

    @Test
    void rejectsUnknownToolAction() {
        ToolExecutor executor = new ToolExecutor(
                mock(VideoSearchTool.class),
                mock(KnowledgeQaTool.class),
                mock(VideoSegmentLocatorTool.class),
                mock(VideoSummaryTool.class),
                mock(QuizTool.class)
        );

        ToolResult result = executor.execute(new ToolCall(
                "未知动作",
                "DeleteEverythingTool",
                Map.of()
        ), AgentState.builder().build());

        assertFalse(result.success());
        assertEquals("DeleteEverythingTool", result.toolName());
    }

    @Test
    void executesSegmentLocatorToolFromPlannerArguments() {
        VideoSegmentLocatorTool locatorTool = mock(VideoSegmentLocatorTool.class);
        when(locatorTool.name()).thenReturn("VideoSegmentLocatorTool");
        ToolExecutor executor = new ToolExecutor(
                mock(VideoSearchTool.class),
                mock(KnowledgeQaTool.class),
                locatorTool,
                mock(VideoSummaryTool.class),
                mock(QuizTool.class)
        );
        AgentState state = AgentState.builder().currentVideoId(42L).build();

        ToolResult result = executor.execute(new ToolCall(
                "定位时间点",
                "VideoSegmentLocatorTool",
                Map.of("query", "RocketMQ", "videoId", 42, "topK", 3)
        ), state);

        assertTrue(result.success());
        verify(locatorTool).execute(org.mockito.ArgumentMatchers.argThat(input ->
                input.query().equals("RocketMQ") && input.videoId().equals(42L) && input.topK().equals(3)
        ), org.mockito.ArgumentMatchers.eq(state));
    }

    @Test
    void executesVideoSummaryToolFromPlannerArguments() {
        VideoSummaryTool summaryTool = mock(VideoSummaryTool.class);
        when(summaryTool.name()).thenReturn("VideoSummaryTool");
        ToolExecutor executor = new ToolExecutor(
                mock(VideoSearchTool.class),
                mock(KnowledgeQaTool.class),
                mock(VideoSegmentLocatorTool.class),
                summaryTool,
                mock(QuizTool.class)
        );
        AgentState state = AgentState.builder().currentVideoId(42L).build();

        ToolResult result = executor.execute(new ToolCall(
                "总结视频",
                "VideoSummaryTool",
                Map.of("topic", "Redis", "videoId", 42, "summaryType", "interview")
        ), state);

        assertTrue(result.success());
        verify(summaryTool).execute(org.mockito.ArgumentMatchers.argThat(input ->
                input.topic().equals("Redis")
                        && input.videoId().equals(42L)
                        && input.summaryType().equals("interview")
        ), org.mockito.ArgumentMatchers.eq(state));
    }

    @Test
    void executesQuizToolFromPlannerArguments() {
        QuizTool quizTool = mock(QuizTool.class);
        when(quizTool.name()).thenReturn("QuizTool");
        ToolExecutor executor = new ToolExecutor(
                mock(VideoSearchTool.class),
                mock(KnowledgeQaTool.class),
                mock(VideoSegmentLocatorTool.class),
                mock(VideoSummaryTool.class),
                quizTool
        );
        AgentState state = AgentState.builder().currentVideoId(42L).build();

        ToolResult result = executor.execute(new ToolCall(
                "生成测验",
                "QuizTool",
                Map.of("topic", "Redis", "videoId", 42, "difficulty", "hard", "count", 3)
        ), state);

        assertTrue(result.success());
        verify(quizTool).execute(org.mockito.ArgumentMatchers.argThat(input ->
                input.topic().equals("Redis")
                        && input.videoId().equals(42L)
                        && input.difficulty().equals("hard")
                        && input.count().equals(3)
        ), org.mockito.ArgumentMatchers.eq(state));
    }
}
