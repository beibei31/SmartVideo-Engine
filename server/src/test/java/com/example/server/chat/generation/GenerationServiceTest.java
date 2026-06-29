package com.example.server.chat.generation;

import com.example.server.chat.memory.ChatMemoryService;
import com.example.server.chat.prompt.PromptBuilder;
import com.example.server.rag.model.SearchResult;
import com.example.server.rag.retrieval.RetrievalService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationServiceTest {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final PromptBuilder promptBuilder = mock(PromptBuilder.class);
    private final ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
    private final StreamingChatLanguageModel streamingChatLanguageModel = mock(StreamingChatLanguageModel.class);
    private final ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);

    @Test
    void syncGenerationUsesConfiguredHybridRetrievalAndSessionHistory() {
        GenerationService service = new GenerationService(
                retrievalService,
                promptBuilder,
                chatLanguageModel,
                streamingChatLanguageModel,
                chatMemoryService
        );
        ReflectionTestUtils.setField(service, "enableHybridRetrieval", true);

        SearchResult context = SearchResult.builder()
                .content("video transcript chunk")
                .score(0.9)
                .sourceTitle("demo.mp4")
                .chunkIndex(1)
                .build();
        List<SearchResult> contexts = List.of(context);
        List<String> history = List.of("user: previous question", "assistant: previous answer");

        when(chatMemoryService.loadHistory("session-1")).thenReturn(history);
        when(retrievalService.search("current question", true, history)).thenReturn(contexts);
        when(promptBuilder.build("current question", contexts, history)).thenReturn("prompt with history");
        when(chatLanguageModel.chat("prompt with history")).thenReturn("answer");

        GenerationService.RagResult result = service.generateWithContexts("current question", "session-1");

        assertEquals("answer", result.answer());
        assertEquals(contexts, result.contexts());
        verify(chatMemoryService).save("session-1", "current question", "answer");
    }
}
