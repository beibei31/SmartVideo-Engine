package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSearchResult;
import com.example.server.agent.model.VideoSegment;
import com.example.server.agent.model.VideoSummaryInput;
import com.example.server.agent.model.VideoSummaryResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoSummaryToolTest {

    @Test
    void summarizesVideoSegmentsWithRequestedType() {
        VideoSearchTool videoSearchTool = mock(VideoSearchTool.class);
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        VideoSummaryTool tool = new VideoSummaryTool(videoSearchTool, chatLanguageModel);
        AgentState state = AgentState.builder().currentVideoId(42L).build();

        when(videoSearchTool.execute(new VideoSearchInput("Redis", 42L, 8), state))
                .thenReturn(new VideoSearchResult(List.of(
                        new VideoSegment(42L, 10L, 30L, "Redis Lua 原子性", 0.9, "redis.mp4", "chunk-1")
                )));
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("面试版总结");

        VideoSummaryResult result = tool.execute(new VideoSummaryInput(42L, "Redis", "interview"), state);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatLanguageModel).chat(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("interview"));
        assertTrue(promptCaptor.getValue().contains("Redis Lua 原子性"));
        assertEquals("interview", result.summaryType());
        assertEquals("面试版总结", result.summary());
        assertEquals(1, result.references().size());
    }
}
