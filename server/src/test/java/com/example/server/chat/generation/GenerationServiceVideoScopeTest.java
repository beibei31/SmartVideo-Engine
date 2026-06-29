package com.example.server.chat.generation;

import com.example.server.chat.memory.ChatMemoryService;
import com.example.server.chat.prompt.PromptBuilder;
import com.example.server.rag.model.RetrievalRequest;
import com.example.server.rag.model.SearchResult;
import com.example.server.rag.retrieval.RetrievalService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationServiceVideoScopeTest {

    @Test
    void syncGenerationPassesVideoIdIntoRetrievalRequest() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        PromptBuilder promptBuilder = mock(PromptBuilder.class);
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        StreamingChatLanguageModel streamingChatLanguageModel = mock(StreamingChatLanguageModel.class);
        ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
        GenerationService service = new GenerationService(
                retrievalService,
                promptBuilder,
                chatLanguageModel,
                streamingChatLanguageModel,
                chatMemoryService
        );
        ReflectionTestUtils.setField(service, "enableHybridRetrieval", true);

        List<String> history = List.of("user: 之前的问题");
        List<SearchResult> contexts = List.of(SearchResult.builder()
                .chunkId("video-42-v1-chunk-0")
                .content("Redis 内容")
                .build());

        when(chatMemoryService.loadHistory("session-1")).thenReturn(history);
        when(retrievalService.search(org.mockito.ArgumentMatchers.any(RetrievalRequest.class))).thenReturn(contexts);
        when(promptBuilder.build("Redis 是什么", contexts, history)).thenReturn("prompt");
        when(chatLanguageModel.chat("prompt")).thenReturn("answer");

        service.generateWithContexts("Redis 是什么", "session-1", 42L);

        ArgumentCaptor<RetrievalRequest> captor = ArgumentCaptor.forClass(RetrievalRequest.class);
        verify(retrievalService).search(captor.capture());
        assertEquals("Redis 是什么", captor.getValue().question());
        assertEquals(42L, captor.getValue().videoId());
        assertEquals(true, captor.getValue().hybrid());
        assertEquals(history, captor.getValue().history());
    }
}
