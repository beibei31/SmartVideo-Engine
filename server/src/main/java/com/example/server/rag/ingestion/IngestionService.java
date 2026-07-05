package com.example.server.rag.ingestion;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.server.rag.embedding.EmbeddingService;
import com.example.server.rag.mapper.RagChunkDocumentMapper;
import com.example.server.rag.mapper.RagVideoVersionMapper;
import com.example.server.rag.model.RagChunkDocument;
import com.example.server.rag.model.RagVideoVersion;
import com.example.server.rag.retrieval.Bm25Index;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IngestionService {

    private final TextDocumentLoader documentLoader;
    private final RecursiveTextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final MilvusEmbeddingStore milvusEmbeddingStore;
    private final Bm25Index bm25Index;
    private final RagChunkDocumentMapper chunkDocumentMapper;
    private final RagVideoVersionMapper videoVersionMapper;

    public IngestionService(TextDocumentLoader documentLoader,
                            RecursiveTextSplitter textSplitter,
                            EmbeddingService embeddingService,
                            MilvusEmbeddingStore milvusEmbeddingStore,
                            Bm25Index bm25Index,
                            RagChunkDocumentMapper chunkDocumentMapper,
                            RagVideoVersionMapper videoVersionMapper) {
        this.documentLoader = documentLoader;
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.milvusEmbeddingStore = milvusEmbeddingStore;
        this.bm25Index = bm25Index;
        this.chunkDocumentMapper = chunkDocumentMapper;
        this.videoVersionMapper = videoVersionMapper;
    }

    public int ingestFromText(String text, String sourceTitle, String sourceType) {
        return ingestFromText(null, text, sourceTitle, sourceType);
    }

    public int ingestFromText(Long videoId, String text, String sourceTitle, String sourceType) {
        if (text == null || text.isBlank()) {
            log.warn("RAG ingest skipped: empty text, title={}", sourceTitle);
            return 0;
        }

        Map<String, Object> baseMetadata = new HashMap<>();
        baseMetadata.put("sourceTitle", sourceTitle);
        baseMetadata.put("sourceType", sourceType);
        baseMetadata.put("videoId", videoId != null ? videoId : "");
        baseMetadata.put("ingestTimestamp", System.currentTimeMillis());

        List<TextSegment> segments = textSplitter.split(text, baseMetadata);
        if (segments.isEmpty()) {
            log.warn("RAG ingest stopped: splitter returned no chunks, title={}", sourceTitle);
            return 0;
        }

        int nextVersion = nextVersion(videoId);

        int successCount = 0;
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            int chunkIndex = readIntMetadata(segment, "chunkIndex", i);
            int totalChunks = readIntMetadata(segment, "totalChunks", segments.size());
            long startTime = readLongMetadata(segment, "startTime", -1L);
            long endTime = readLongMetadata(segment, "endTime", -1L);
            String chunkId = buildChunkId(videoId, sourceTitle, nextVersion, chunkIndex, segment.text());

            try {
                TextSegment enrichedSegment = enrichSegment(
                        segment,
                        chunkId,
                        videoId,
                        sourceTitle,
                        sourceType,
                        chunkIndex,
                        totalChunks,
                        startTime,
                        endTime,
                        nextVersion
                );

                Embedding embedding = Embedding.from(embeddingService.embed(enrichedSegment.text()));
                milvusEmbeddingStore.add(embedding, enrichedSegment);

                RagChunkDocument doc = toChunkDocument(
                        enrichedSegment,
                        chunkId,
                        videoId,
                        sourceTitle,
                        sourceType,
                        chunkIndex,
                        totalChunks,
                        startTime,
                        endTime,
                        nextVersion
                );
                chunkDocumentMapper.insert(doc);

                bm25Index.index(chunkId, enrichedSegment.text(), sourceTitle, chunkIndex);
                successCount++;
            } catch (Exception e) {
                log.error("RAG chunk ingest failed: chunkIndex={}, title={}, error={}",
                        chunkIndex, sourceTitle, e.getMessage(), e);
            }
        }

        if (videoId != null && successCount > 0) {
            markOlderVersionsDeleted(videoId, nextVersion);
            publishCurrentVersion(videoId, nextVersion);
        }

        log.info("RAG ingest complete: title={}, success={}/{}", sourceTitle, successCount, segments.size());
        return successCount;
    }

    public int ingestFromFile(String filePath, String sourceType) {
        String sourceTitle = filePath.substring(
                Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1
        );
        return ingestFromText(documentLoader.load(filePath), sourceTitle, sourceType);
    }

    private TextSegment enrichSegment(TextSegment segment,
                                      String chunkId,
                                      Long videoId,
                                      String sourceTitle,
                                      String sourceType,
                                      int chunkIndex,
                                      int totalChunks,
                                      long startTime,
                                      long endTime,
                                      int version) {
        Metadata metadata = new Metadata();
        if (segment.metadata() != null) {
            segment.metadata().toMap().forEach((key, value) ->
                    metadata.put(key, value != null ? value.toString() : ""));
        }
        metadata.put("chunkId", chunkId);
        metadata.put("videoId", videoId != null ? String.valueOf(videoId) : "");
        metadata.put("sourceTitle", sourceTitle != null ? sourceTitle : "");
        metadata.put("sourceType", sourceType != null ? sourceType : "");
        metadata.put("chunkIndex", String.valueOf(chunkIndex));
        metadata.put("totalChunks", String.valueOf(totalChunks));
        metadata.put("startTime", String.valueOf(startTime));
        metadata.put("endTime", String.valueOf(endTime));
        metadata.put("deleted", "false");
        metadata.put("version", String.valueOf(version));
        return TextSegment.from(segment.text(), metadata);
    }

    private RagChunkDocument toChunkDocument(TextSegment segment,
                                             String chunkId,
                                             Long videoId,
                                             String sourceTitle,
                                             String sourceType,
                                             int chunkIndex,
                                             int totalChunks,
                                             long startTime,
                                             long endTime,
                                             int version) {
        RagChunkDocument doc = new RagChunkDocument();
        doc.setChunkId(chunkId);
        doc.setVideoId(videoId);
        doc.setTitle(sourceTitle);
        doc.setSourceType(sourceType);
        doc.setChunkIndex(chunkIndex);
        doc.setTotalChunks(totalChunks);
        doc.setStartTime(startTime >= 0 ? startTime : null);
        doc.setEndTime(endTime >= 0 ? endTime : null);
        doc.setDeleted(false);
        doc.setVersion(version);
        doc.setContent(segment.text());
        doc.setMetadataJson(JSON.toJSONString(segment.metadata().toMap()));
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        return doc;
    }

    private int readIntMetadata(TextSegment segment, String key, int fallback) {
        String value = segment.metadata() != null ? segment.metadata().getString(key) : null;
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long readLongMetadata(TextSegment segment, String key, long fallback) {
        String value = segment.metadata() != null ? segment.metadata().getString(key) : null;
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String buildChunkId(Long videoId, String sourceTitle, int version, int chunkIndex, String content) {
        String owner = videoId != null ? "video-" + videoId : "text-" + sha256(sourceTitle != null ? sourceTitle : "");
        return owner + "-v" + version + "-chunk-" + chunkIndex + "-" + sha256(content).substring(0, 12);
    }

    private int nextVersion(Long videoId) {
        if (videoId == null) {
            return 1;
        }
        RagVideoVersion version = videoVersionMapper.selectById(videoId);
        if (version == null || version.getCurrentVersion() == null) {
            return 1;
        }
        return version.getCurrentVersion() + 1;
    }

    private void markOlderVersionsDeleted(Long videoId, int currentVersion) {
        RagChunkDocument update = new RagChunkDocument();
        update.setDeleted(true);
        update.setUpdatedAt(LocalDateTime.now());
        chunkDocumentMapper.update(update, new LambdaQueryWrapper<RagChunkDocument>()
                .eq(RagChunkDocument::getVideoId, videoId)
                .lt(RagChunkDocument::getVersion, currentVersion));
    }

    private void publishCurrentVersion(Long videoId, int currentVersion) {
        RagVideoVersion version = new RagVideoVersion();
        version.setVideoId(videoId);
        version.setCurrentVersion(currentVersion);
        version.setUpdatedAt(LocalDateTime.now());
        if (videoVersionMapper.selectById(videoId) == null) {
            videoVersionMapper.insert(version);
        } else {
            videoVersionMapper.updateById(version);
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text != null ? text : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
