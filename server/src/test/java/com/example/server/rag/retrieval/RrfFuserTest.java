package com.example.server.rag.retrieval;

import com.example.server.rag.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RrfFuserTest {

    @Test
    void fusedResultsAreStableAndDeduplicatedByChunkId() {
        RrfFuser fuser = new RrfFuser();

        SearchResult denseA = result("a", "dense-a", 0.90);
        SearchResult denseB = result("b", "dense-b", 0.80);
        SearchResult sparseB = result("b", "sparse-b", 9.00);
        SearchResult sparseC = result("c", "sparse-c", 8.00);

        List<SearchResult> fused = fuser.fuse(
                List.of(denseA, denseB),
                List.of(sparseB, sparseC),
                10
        );

        assertEquals(3, fused.size());
        assertEquals("b", fused.get(0).getChunkId());
        assertEquals("a", fused.get(1).getChunkId());
        assertEquals("c", fused.get(2).getChunkId());
        assertEquals("HYBRID", fused.get(0).getRetrievalType());
        assertEquals("dense-b", fused.get(0).getContent());
    }

    private SearchResult result(String chunkId, String content, double score) {
        return SearchResult.builder()
                .chunkId(chunkId)
                .content(content)
                .score(score)
                .build();
    }
}
