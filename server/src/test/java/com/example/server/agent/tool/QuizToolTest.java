package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.QuizInput;
import com.example.server.agent.model.QuizResult;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSearchResult;
import com.example.server.agent.model.VideoSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizToolTest {

    @Test
    void generatesQuizFromVideoSegments() {
        VideoSearchTool videoSearchTool = mock(VideoSearchTool.class);
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        QuizTool quizTool = new QuizTool(videoSearchTool, chatLanguageModel);
        AgentState state = AgentState.builder().currentVideoId(42L).build();

        when(videoSearchTool.execute(new VideoSearchInput("Redis", 42L, 8), state))
                .thenReturn(new VideoSearchResult(List.of(
                        new VideoSegment(42L, 10L, 30L, "Redis 缓存击穿与互斥锁", 0.9, "redis.mp4", "chunk-1")
                )));
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("1. Redis 缓存击穿是什么？");

        QuizResult result = quizTool.execute(new QuizInput(42L, "Redis", "medium", 5), state);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatLanguageModel).chat(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("5"));
        assertTrue(promptCaptor.getValue().contains("medium"));
        assertTrue(promptCaptor.getValue().contains("Redis 缓存击穿与互斥锁"));
        assertEquals("Redis", result.topic());
        assertEquals("medium", result.difficulty());
        assertEquals(5, result.count());
        assertEquals("1. Redis 缓存击穿是什么？", result.quiz());
    }
}
