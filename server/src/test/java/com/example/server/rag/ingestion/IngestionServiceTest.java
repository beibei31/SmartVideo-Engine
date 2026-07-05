package com.example.server.rag.ingestion;

import com.example.server.rag.embedding.EmbeddingService;
import com.example.server.rag.mapper.RagChunkDocumentMapper;
import com.example.server.rag.mapper.RagVideoVersionMapper;
import com.example.server.rag.model.RagChunkDocument;
import com.example.server.rag.model.RagVideoVersion;
import com.example.server.rag.retrieval.Bm25Index;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.mockito.ArgumentCaptor;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IngestionServiceTest {

    @Test
    void ingestByMediaIdPersistsChunkDocumentAndIndexesBm25WithStableChunkId() {
        TextDocumentLoader documentLoader = mock(TextDocumentLoader.class);
        RecursiveTextSplitter textSplitter = mock(RecursiveTextSplitter.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        MilvusEmbeddingStore milvusEmbeddingStore = mock(MilvusEmbeddingStore.class);
        Bm25Index bm25Index = mock(Bm25Index.class);
        RagChunkDocumentMapper chunkDocumentMapper = mock(RagChunkDocumentMapper.class);
        RagVideoVersionMapper videoVersionMapper = mock(RagVideoVersionMapper.class);

        TextSegment segment = TextSegment.from("hello bm25 and milvus");
        when(textSplitter.split(any(), any())).thenReturn(List.of(segment));
        when(embeddingService.embed("hello bm25 and milvus")).thenReturn(new float[]{0.1f, 0.2f});

        IngestionService service = new IngestionService(
                documentLoader,
                textSplitter,
                embeddingService,
                milvusEmbeddingStore,
                bm25Index,
                chunkDocumentMapper,
                videoVersionMapper
        );

        int count = service.ingestFromText(42L, "transcript", "video title", "ASR");

        assertEquals(1, count);
        verify(chunkDocumentMapper).insert(any(RagChunkDocument.class));
        verify(milvusEmbeddingStore).add(any(), any(TextSegment.class));
        verify(bm25Index).index(
                org.mockito.ArgumentMatchers.startsWith("video-42-v1-chunk-"),
                org.mockito.ArgumentMatchers.eq("hello bm25 and milvus"),
                org.mockito.ArgumentMatchers.eq("video title"),
                org.mockito.ArgumentMatchers.eq(0)
        );
    }

    @Test
    void ingestByMediaIdMarksOldChunksDeletedAndWritesNextVersion() {
        TextDocumentLoader documentLoader = mock(TextDocumentLoader.class);
        RecursiveTextSplitter textSplitter = mock(RecursiveTextSplitter.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        MilvusEmbeddingStore milvusEmbeddingStore = mock(MilvusEmbeddingStore.class);
        Bm25Index bm25Index = mock(Bm25Index.class);
        RagChunkDocumentMapper chunkDocumentMapper = mock(RagChunkDocumentMapper.class);
        RagVideoVersionMapper videoVersionMapper = mock(RagVideoVersionMapper.class);

        TextSegment segment = TextSegment.from("fresh transcript");
        when(textSplitter.split(any(), any())).thenReturn(List.of(segment));
        when(embeddingService.embed("fresh transcript")).thenReturn(new float[]{0.1f});
        RagVideoVersion currentVersion = new RagVideoVersion();
        currentVersion.setVideoId(42L);
        currentVersion.setCurrentVersion(1);
        when(videoVersionMapper.selectById(42L)).thenReturn(currentVersion);

        IngestionService service = new IngestionService(
                documentLoader,
                textSplitter,
                embeddingService,
                milvusEmbeddingStore,
                bm25Index,
                chunkDocumentMapper,
                videoVersionMapper
        );

        service.ingestFromText(42L, "transcript", "video title", "ASR");

        ArgumentCaptor<RagChunkDocument> updateCaptor = ArgumentCaptor.forClass(RagChunkDocument.class);
        verify(chunkDocumentMapper).update(updateCaptor.capture(), any(LambdaQueryWrapper.class));
        assertEquals(true, updateCaptor.getValue().getDeleted());

        ArgumentCaptor<RagChunkDocument> insertCaptor = ArgumentCaptor.forClass(RagChunkDocument.class);
        verify(chunkDocumentMapper).insert(insertCaptor.capture());
        assertEquals(2, insertCaptor.getValue().getVersion());
        assertFalse(insertCaptor.getValue().getDeleted());
        verify(videoVersionMapper).updateById(any(RagVideoVersion.class));
    }
}
