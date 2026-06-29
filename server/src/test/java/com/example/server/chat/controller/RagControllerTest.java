package com.example.server.chat.controller;

import com.example.server.chat.generation.GenerationService;
import com.example.server.chat.memory.ChatMemoryService;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.rag.ingestion.IngestionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagControllerTest {

    @Test
    void syncChatPassesVideoIdToGenerationService() {
        GenerationService generationService = mock(GenerationService.class);
        RagController controller = new RagController(
                generationService,
                mock(ChatMemoryService.class),
                mock(IngestionService.class),
                mock(MediaFileMapper.class)
        );
        when(generationService.generateWithContexts("Redis 是什么", "session-1", 42L))
                .thenReturn(new GenerationService.RagResult("answer", List.of()));

        Map<String, Object> response = controller.chatSync(Map.of(
                "question", "Redis 是什么",
                "sessionId", "session-1",
                "videoId", "42"
        ));

        assertEquals("answer", response.get("answer"));
        verify(generationService).generateWithContexts("Redis 是什么", "session-1", 42L);
    }
}
