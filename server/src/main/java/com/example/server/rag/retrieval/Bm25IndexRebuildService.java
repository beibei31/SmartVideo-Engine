package com.example.server.rag.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.server.rag.mapper.RagChunkDocumentMapper;
import com.example.server.rag.model.RagChunkDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class Bm25IndexRebuildService implements ApplicationRunner {

    private final RagChunkDocumentMapper chunkDocumentMapper;
    private final Bm25Index bm25Index;
    private final boolean rebuildOnStartup;

    public Bm25IndexRebuildService(RagChunkDocumentMapper chunkDocumentMapper,
                                   Bm25Index bm25Index,
                                   @Value("${rag.bm25.rebuild-on-startup:true}") boolean rebuildOnStartup) {
        this.chunkDocumentMapper = chunkDocumentMapper;
        this.bm25Index = bm25Index;
        this.rebuildOnStartup = rebuildOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!rebuildOnStartup) {
            log.info("BM25 startup rebuild disabled");
            return;
        }
        rebuild();
    }

    public void rebuild() {
        List<RagChunkDocument> chunks = chunkDocumentMapper.selectList(new LambdaQueryWrapper<RagChunkDocument>()
                .eq(RagChunkDocument::getDeleted, false));
        bm25Index.clear();
        int count = 0;
        for (RagChunkDocument chunk : chunks) {
            if (chunk.getChunkId() == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            bm25Index.index(
                    chunk.getChunkId(),
                    chunk.getContent(),
                    chunk.getTitle(),
                    chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0
            );
            count++;
        }
        log.info("BM25 index rebuilt from MySQL: indexed={} persisted={}", count, chunks.size());
    }
}
