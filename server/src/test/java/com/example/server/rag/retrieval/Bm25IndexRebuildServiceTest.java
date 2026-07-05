package com.example.server.rag.retrieval;

import com.example.server.rag.mapper.RagChunkDocumentMapper;
import com.example.server.rag.model.RagChunkDocument;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class Bm25IndexRebuildServiceTest {

    @Test
    void rebuildIndexesAllPersistedChunksFromMysql() {
        RagChunkDocumentMapper mapper = mock(RagChunkDocumentMapper.class);
        Bm25Index bm25Index = mock(Bm25Index.class);

        RagChunkDocument first = new RagChunkDocument();
        first.setChunkId("chunk-1");
        first.setContent("first chunk content");
        first.setTitle("video A");
        first.setChunkIndex(0);

        RagChunkDocument second = new RagChunkDocument();
        second.setChunkId("chunk-2");
        second.setContent("second chunk content");
        second.setTitle("video B");
        second.setChunkIndex(1);

        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        Bm25IndexRebuildService service = new Bm25IndexRebuildService(mapper, bm25Index, true);
        service.rebuild();

        verify(bm25Index).clear();
        verify(bm25Index).index("chunk-1", "first chunk content", "video A", 0);
        verify(bm25Index).index("chunk-2", "second chunk content", "video B", 1);
    }
}
