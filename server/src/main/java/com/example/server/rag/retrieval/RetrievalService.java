package com.example.server.rag.retrieval;

import com.example.server.rag.embedding.EmbeddingService;
import com.example.server.rag.mapper.RagVideoVersionMapper;
import com.example.server.rag.model.RagVideoVersion;
import com.example.server.rag.model.RetrievalRequest;
import com.example.server.rag.model.SearchResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RetrievalService {

    private static final int DENSE_TOP_K = 20;
    private static final int SPARSE_TOP_K = 20;
    private static final int FUSION_TOP_K = 10;
    private static final int RERANK_TOP_K = 5;

    private final EmbeddingService embeddingService;
    private final MilvusEmbeddingStore milvusEmbeddingStore;
    private final QueryRewriter queryRewriter;
    private final RrfFuser rrfFuser;
    private final RerankerService rerankerService;
    private final Bm25Index bm25Index;
    private final RagChunkValidator ragChunkValidator;
    private final RagVideoVersionMapper videoVersionMapper;

    @Value("${milvus.similarity-threshold:0.6}")
    private double similarityThreshold;

    public RetrievalService(EmbeddingService embeddingService,
                            MilvusEmbeddingStore milvusEmbeddingStore,
                            QueryRewriter queryRewriter,
                            RrfFuser rrfFuser,
                            RerankerService rerankerService,
                            Bm25Index bm25Index,
                            RagChunkValidator ragChunkValidator,
                            RagVideoVersionMapper videoVersionMapper) {
        this.embeddingService = embeddingService;
        this.milvusEmbeddingStore = milvusEmbeddingStore;
        this.queryRewriter = queryRewriter;
        this.rrfFuser = rrfFuser;
        this.rerankerService = rerankerService;
        this.bm25Index = bm25Index;
        this.ragChunkValidator = ragChunkValidator;
        this.videoVersionMapper = videoVersionMapper;
    }

    public List<SearchResult> search(String rawQuestion) {
        return search(rawQuestion, false);
    }

    public List<SearchResult> search(String rawQuestion, boolean enableHybrid) {
        return search(rawQuestion, enableHybrid, List.of());
    }

    public List<SearchResult> search(String rawQuestion, boolean enableHybrid, List<String> history) {
        return search(RetrievalRequest.builder()
                .question(rawQuestion)
                .hybrid(enableHybrid)
                .history(history)
                .build());
    }

    public List<SearchResult> search(RetrievalRequest request) {
        String rewrittenQuery = queryRewriter.rewrite(request.question(), request.history());
        log.info("RAG retrieval started: raw='{}', rewritten='{}', hybrid={}",
                shorten(request.question()), shorten(rewrittenQuery), request.hybrid());

        Integer currentVersion = resolveCurrentVersion(request.videoId());
        List<SearchResult> denseResults = denseSearch(rewrittenQuery, request.videoId(), currentVersion, request.hybrid());
        List<SearchResult> candidates;
        if (request.hybrid()) {
            List<SearchResult> sparseResults = sparseSearch(rewrittenQuery);
            candidates = rrfFuser.fuse(denseResults, sparseResults, FUSION_TOP_K);
            log.info("Hybrid retrieval candidates: dense={}, bm25={}, fused={}",
                    denseResults.size(), sparseResults.size(), candidates.size());
        } else {
            candidates = denseResults;
        }

        candidates = ragChunkValidator.keepActive(candidates);
        List<SearchResult> finalResults = rerankerService.rerank(rewrittenQuery, candidates, RERANK_TOP_K);
        log.info("RAG retrieval complete: candidates={}, final={}",
                candidates.size(), finalResults.size());
        return finalResults;
    }

    private List<SearchResult> denseSearch(String query, Long videoId, Integer currentVersion, boolean allowFallback) {
        try {
            float[] queryVector = embeddingService.embed(query);
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(Embedding.from(queryVector))
                    .maxResults(DENSE_TOP_K)
                    .minScore(similarityThreshold);

            Filter filter = buildMilvusFilter(videoId, currentVersion);
            if (filter != null) {
                builder.filter(filter);
            }

            EmbeddingSearchRequest request = builder.build();

            List<EmbeddingMatch<TextSegment>> matches = milvusEmbeddingStore.search(request).matches();
            List<SearchResult> results = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                String chunkId = segment.metadata() != null ? segment.metadata().getString("chunkId") : null;
                SearchResult result = SearchResult.builder()
                        .chunkId(chunkId != null && !chunkId.isBlank() ? chunkId : match.embeddingId())
                        .content(segment.text())
                        .score(match.score())
                        .retrievalType("DENSE")
                        .build();

                if (segment.metadata() != null) {
                    result.setSourceTitle(segment.metadata().getString("sourceTitle"));
                    String chunkIndex = segment.metadata().getString("chunkIndex");
                    if (chunkIndex != null && !chunkIndex.isBlank()) {
                        result.setChunkIndex(Integer.parseInt(chunkIndex));
                    }
                    result.setMetadata(segment.metadata().toMap());
                }
                results.add(result);
            }
            return results;
        } catch (RuntimeException e) {
            if (!allowFallback) {
                throw e;
            }
            log.warn("Dense retrieval failed; falling back to BM25 candidates: {}", e.getMessage());
            return List.of();
        }
    }

    private List<SearchResult> sparseSearch(String query) {
        return bm25Index.search(query, SPARSE_TOP_K);
    }

    private Integer resolveCurrentVersion(Long videoId) {
        if (videoId == null) {
            return null;
        }
        RagVideoVersion version = videoVersionMapper.selectById(videoId);
        return version != null ? version.getCurrentVersion() : null;
    }

    private Filter buildMilvusFilter(Long videoId, Integer currentVersion) {
        if (videoId == null) {
            return null;
        }
        Filter filter = new And(
                new IsEqualTo("videoId", String.valueOf(videoId)),
                new IsEqualTo("deleted", "false")
        );
        if (currentVersion != null) {
            filter = new And(filter, new IsEqualTo("version", String.valueOf(currentVersion)));
        }
        return filter;
    }

    private String shorten(String text) {
        if (text == null || text.length() <= 50) {
            return text;
        }
        return text.substring(0, 50) + "...";
    }
}
