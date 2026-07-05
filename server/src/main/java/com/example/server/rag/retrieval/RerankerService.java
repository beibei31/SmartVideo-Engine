package com.example.server.rag.retrieval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.server.rag.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cross-Encoder 重排序服务
 *
 * 为什么需要重排序：
 *   向量/BM25 检索是"粗排"——追求快速召回足够多的候选，排序不够精准。
 *   重排序用更强的 Cross-Encoder 模型做"精排"——对每个 (query, chunk) 对
 *   做交叉注意力计算，输出精确的相关性分数。
 *
 * 模型：BAAI/bge-reranker-v2-m3
 * 流程：粗排 Top-20 → 重排序打分 → 最终输出 Top-5
 *
 * 注意：reranker.enabled=false 时直接透传，不做重排序
 */
@Slf4j
@Service
public class RerankerService {

    private final OkHttpClient httpClient;

    @Value("${reranker.base-url:http://localhost:8081}")
    private String rerankerBaseUrl;

    @Value("${reranker.model-name:BAAI/bge-reranker-v2-m3}")
    private String rerankerModel;

    /** 是否启用重排序（没有部署模型时关掉） */
    @Value("${reranker.enabled:false}")
    private boolean enabled;

    public RerankerService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 对候选列表重排序
     *
     * @param query      用户查询
     * @param candidates 粗排候选列表（Top-20）
     * @param topK       最终返回数量（Top-5）
     * @return 精排后的结果列表
     */
    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        if (!enabled) {
            log.debug("Reranker 未启用，直接透传 Top-{}", topK);
            int limit = Math.min(topK, candidates != null ? candidates.size() : 0);
            return limit > 0 ? new ArrayList<>(candidates.subList(0, limit)) : List.of();
        }

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        try {
            // 调用 Reranker API（OpenAI 兼容格式）
            List<ScoredIndex> scored = callRerankerApi(query, candidates);

            // 按重排序分数降序排列
            scored.sort(Comparator.comparingDouble(ScoredIndex::score).reversed());

            // 取 Top-K
            List<SearchResult> reranked = new ArrayList<>();
            int limit = Math.min(topK, scored.size());
            for (int i = 0; i < limit; i++) {
                ScoredIndex si = scored.get(i);
                SearchResult r = candidates.get(si.index());
                r.setScore(si.score());
                r.setRetrievalType("RERANKED");
                reranked.add(r);
            }

            log.debug("重排序完成: {}条 → {}条", candidates.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("重排序失败，回退到原始排序: {}", e.getMessage());
            int limit = Math.min(topK, candidates.size());
            return new ArrayList<>(candidates.subList(0, limit));
        }
    }

    /**
     * 调用 Reranker API
     * 支持 OpenAI 兼容的 /v1/rerank 端点
     */
    private List<ScoredIndex> callRerankerApi(String query, List<SearchResult> candidates) throws IOException {
        // 提取所有候选文本
        List<String> documents = new ArrayList<>();
        for (SearchResult r : candidates) {
            documents.add(r.getContent());
        }

        // 构建请求
        JSONObject body = new JSONObject();
        body.put("model", rerankerModel);
        body.put("query", query);
        JSONArray docs = new JSONArray();
        docs.addAll(documents);
        body.put("documents", docs);

        Request request = new Request.Builder()
                .url(rerankerBaseUrl + "/v1/rerank")
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                        body.toJSONString(),
                        MediaType.parse("application/json; charset=utf-8")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Reranker API error: " + response.code());
            }

            String respBody = response.body() != null ? response.body().string() : "{}";
            JSONObject respJson = JSON.parseObject(respBody);

            // 解析 result 数组：[{index: 0, relevance_score: 0.95}, ...]
            JSONArray results = respJson.getJSONArray("results");
            List<ScoredIndex> scored = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                scored.add(new ScoredIndex(
                        item.getIntValue("index"),
                        item.getDoubleValue("relevance_score")
                ));
            }
            return scored;
        }
    }

    /** 内部类：候选索引 + 重排序分数 */
    private record ScoredIndex(int index, double score) {}
}
