package com.example.server.rag.retrieval;

import com.example.server.rag.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF) 融合器
 *
 * 解决问题：向量检索分数 [0,1] 和 BM25 分数 [0,+∞) 不可直接比较
 *
 * 算法：不看分数，看排名
 *   RRF(d) = Σ 1 / (k + rank_i(d))
 *   其中 k=60 是平滑常数，rank_i 是文档在第 i 路检索中的排名（从 1 开始）
 *
 * 示例：
 *   chunk_X: 向量排名第1, BM25排名第3 → 1/(60+1) + 1/(60+3) = 0.0325  (最高)
 *   chunk_Y: 向量排名第4, BM25排名第1 → 1/(60+4) + 1/(60+1) = 0.0320
 */
@Slf4j
@Component
public class RrfFuser {

    /** 平滑常数，防止排名第1权重过大 */
    private static final double K = 60.0;

    /**
     * 融合两路排名
     *
     * @param denseResults  向量检索结果（已按分数降序）
     * @param sparseResults BM25 检索结果（已按分数降序）
     * @param topK          最终返回 Top-K
     * @return 融合后的结果列表，按 RRF 总分降序
     */
    public List<SearchResult> fuse(
            List<SearchResult> denseResults,
            List<SearchResult> sparseResults,
            int topK) {

        // 如果只有一路有结果，直接返回那一路的 Top-K
        if (sparseResults == null || sparseResults.isEmpty()) {
            int limit = Math.min(topK, denseResults != null ? denseResults.size() : 0);
            return limit > 0 ? new ArrayList<>(denseResults.subList(0, limit)) : List.of();
        }
        if (denseResults == null || denseResults.isEmpty()) {
            int limit = Math.min(topK, sparseResults.size());
            return new ArrayList<>(sparseResults.subList(0, limit));
        }

        // chunkId → RRF 总分
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        // chunkId → SearchResult（保留第一个出现的）
        Map<String, SearchResult> resultMap = new LinkedHashMap<>();

        // 计算向量检索路的 RRF 贡献
        for (int i = 0; i < denseResults.size(); i++) {
            SearchResult r = denseResults.get(i);
            String key = resolveKey(r);
            double rrfScore = 1.0 / (K + i + 1); // rank = i + 1
            rrfScores.put(key, rrfScores.getOrDefault(key, 0.0) + rrfScore);
            resultMap.putIfAbsent(key, r);
        }

        // 计算 BM25 检索路的 RRF 贡献
        for (int i = 0; i < sparseResults.size(); i++) {
            SearchResult r = sparseResults.get(i);
            String key = resolveKey(r);
            double rrfScore = 1.0 / (K + i + 1);
            rrfScores.put(key, rrfScores.getOrDefault(key, 0.0) + rrfScore);
            if (!resultMap.containsKey(key)) {
                resultMap.put(key, r);
            }
        }

        // 按 RRF 总分降序排列，取 Top-K
        List<SearchResult> fused = new ArrayList<>();
        rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .forEach(entry -> {
                    SearchResult r = resultMap.get(entry.getKey());
                    r.setScore(entry.getValue());      // 覆盖为 RRF 分数
                    r.setRetrievalType("HYBRID");       // 标记为融合结果
                    fused.add(r);
                });

        log.debug("RRF 融合: 向量{}条 + BM25{}条 → {}条",
                denseResults.size(), sparseResults.size(), fused.size());
        return fused;
    }

    /**
     * 解析 chunk 的唯一标识
     * 优先用 chunkId，其次用内容哈希作为 fallback
     */
    private String resolveKey(SearchResult r) {
        if (r.getChunkId() != null && !r.getChunkId().isBlank()) {
            return r.getChunkId();
        }
        // 用内容作为 fallback key
        return String.valueOf(r.getContent() != null ? r.getContent().hashCode() : r.hashCode());
    }
}
