package com.example.server.rag.retrieval;

import com.example.server.rag.embedding.EmbeddingService;
import com.example.server.rag.mapper.RagVideoVersionMapper;
import com.example.server.rag.model.RagVideoVersion;
import com.example.server.rag.model.RetrievalRequest;
import com.example.server.rag.model.SearchResult;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    @Test
    void scopedSearchAddsMilvusFilterForCurrentVideoVersion() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        MilvusEmbeddingStore milvusEmbeddingStore = mock(MilvusEmbeddingStore.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        RrfFuser rrfFuser = mock(RrfFuser.class);
        RerankerService rerankerService = mock(RerankerService.class);
        Bm25Index bm25Index = mock(Bm25Index.class);
        RagChunkValidator ragChunkValidator = mock(RagChunkValidator.class);
        RagVideoVersionMapper videoVersionMapper = mock(RagVideoVersionMapper.class);

        RetrievalService service = new RetrievalService(
                embeddingService,
                milvusEmbeddingStore,
                queryRewriter,
                rrfFuser,
                rerankerService,
                bm25Index,
                ragChunkValidator,
                videoVersionMapper
        );
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.6);

        RagVideoVersion version = new RagVideoVersion();
        version.setVideoId(42L);
        version.setCurrentVersion(3);

        Metadata metadata = Metadata.from(Map.of(
                "chunkId", "video-42-v3-chunk-0",
                "videoId", "42",
                "version", "3",
                "deleted", "false",
                "sourceTitle", "demo.mp4",
                "chunkIndex", "0"
        ));
        TextSegment segment = TextSegment.from("Redis 缓存击穿", metadata);
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                0.9,
                "embedding-id",
                Embedding.from(new float[]{0.1f, 0.2f}),
                segment
        );

        when(videoVersionMapper.selectById(42L)).thenReturn(version);
        when(queryRewriter.rewrite("Redis 缓存击穿", List.of())).thenReturn("Redis 缓存击穿");
        when(embeddingService.embed("Redis 缓存击穿")).thenReturn(new float[]{0.1f, 0.2f});
        when(milvusEmbeddingStore.search(org.mockito.ArgumentMatchers.any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(match)));
        when(ragChunkValidator.keepActive(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(rerankerService.rerank(org.mockito.ArgumentMatchers.eq("Redis 缓存击穿"),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(5)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        service.search(RetrievalRequest.builder()
                .question("Redis 缓存击穿")
                .videoId(42L)
                .hybrid(false)
                .history(List.of())
                .build());

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(milvusEmbeddingStore).search(captor.capture());

        Filter filter = captor.getValue().filter();
        assertNotNull(filter);
        assertContainsEquality(filter, "videoId", "42");
        assertContainsEquality(filter, "version", "3");
        assertContainsEquality(filter, "deleted", "false");
    }

    @Test
    void hybridSearchFallsBackToBm25WhenDenseSearchFails() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        MilvusEmbeddingStore milvusEmbeddingStore = mock(MilvusEmbeddingStore.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        RrfFuser rrfFuser = mock(RrfFuser.class);
        RerankerService rerankerService = mock(RerankerService.class);
        Bm25Index bm25Index = mock(Bm25Index.class);
        RagChunkValidator ragChunkValidator = mock(RagChunkValidator.class);
        RagVideoVersionMapper videoVersionMapper = mock(RagVideoVersionMapper.class);

        RetrievalService service = new RetrievalService(
                embeddingService,
                milvusEmbeddingStore,
                queryRewriter,
                rrfFuser,
                rerankerService,
                bm25Index,
                ragChunkValidator,
                videoVersionMapper
        );

        SearchResult sparse = SearchResult.builder()
                .chunkId("chunk-1")
                .content("Redis 缓存击穿")
                .score(1.2)
                .retrievalType("SPARSE")
                .build();

        when(queryRewriter.rewrite("Redis 缓存击穿", List.of())).thenReturn("Redis 缓存击穿");
        when(embeddingService.embed("Redis 缓存击穿")).thenThrow(new IllegalStateException("ollama unavailable"));
        when(bm25Index.search("Redis 缓存击穿", 20)).thenReturn(List.of(sparse));
        when(rrfFuser.fuse(org.mockito.ArgumentMatchers.eq(List.of()),
                org.mockito.ArgumentMatchers.eq(List.of(sparse)),
                org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(sparse));
        when(ragChunkValidator.keepActive(List.of(sparse))).thenReturn(List.of(sparse));
        when(rerankerService.rerank("Redis 缓存击穿", List.of(sparse), 5)).thenReturn(List.of(sparse));

        List<SearchResult> results = service.search(RetrievalRequest.builder()
                .question("Redis 缓存击穿")
                .hybrid(true)
                .history(List.of())
                .build());

        assertEquals(List.of(sparse), results);
        verify(bm25Index).search("Redis 缓存击穿", 20);
    }

    private void assertContainsEquality(Filter filter, String key, Object value) {
        if (filter instanceof And and) {
            try {
                assertContainsEquality(and.left(), key, value);
                return;
            } catch (AssertionError ignored) {
                assertContainsEquality(and.right(), key, value);
                return;
            }
        }
        IsEqualTo equalTo = assertInstanceOf(IsEqualTo.class, filter);
        assertEquals(key, equalTo.key());
        assertEquals(value, equalTo.comparisonValue());
    }
}
