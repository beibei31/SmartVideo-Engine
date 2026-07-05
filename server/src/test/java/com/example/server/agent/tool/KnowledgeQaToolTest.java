package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.KnowledgeQaInput;
import com.example.server.agent.model.KnowledgeQaResult;
import com.example.server.chat.generation.GenerationService;
import com.example.server.rag.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeQaToolTest {

    @Test
    void answersWithScopedRagGeneration() {
        GenerationService generationService = mock(GenerationService.class);
        KnowledgeQaTool tool = new KnowledgeQaTool(generationService);
        AgentState state = AgentState.builder()
                .sessionId("session-1")
                .currentVideoId(42L)
                .build();
        List<SearchResult> contexts = List.of(SearchResult.builder()
                .chunkId("video-42-v1-chunk-0")
                .content("Redis 内容")
                .build());

        when(generationService.generateWithContexts("Redis 为什么用 Lua", "session-1", 42L))
                .thenReturn(new GenerationService.RagResult("因为 Lua 能保证原子性", contexts));

        KnowledgeQaResult result = tool.execute(new KnowledgeQaInput("Redis 为什么用 Lua", null), state);

        assertEquals("因为 Lua 能保证原子性", result.answer());
        assertEquals(contexts, result.contexts());
        verify(generationService).generateWithContexts("Redis 为什么用 Lua", "session-1", 42L);
    }
}
