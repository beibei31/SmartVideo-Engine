package com.example.server.rag.retrieval;

import com.example.server.rag.mapper.RagChunkDocumentMapper;
import com.example.server.rag.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagChunkValidatorTest {

    @Test
    void keepsOnlyChunkIdsThatMysqlMarksAsActiveInOneBatch() {
        RagChunkDocumentMapper mapper = mock(RagChunkDocumentMapper.class);
        RagChunkValidator validator = new RagChunkValidator(mapper);

        SearchResult oldChunk = SearchResult.builder()
                .chunkId("old")
                .content("old content")
                .build();
        SearchResult currentChunk = SearchResult.builder()
                .chunkId("current")
                .content("current content")
                .build();

        when(mapper.selectActiveChunkIds(List.of("old", "current"))).thenReturn(List.of("current"));

        List<SearchResult> filtered = validator.keepActive(List.of(oldChunk, currentChunk));

        assertEquals(1, filtered.size());
        assertEquals("current", filtered.get(0).getChunkId());
    }
}
