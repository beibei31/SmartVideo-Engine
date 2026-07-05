package com.example.server.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 向量化服务
 * 包装 LangChain4j EmbeddingModel，提供批量向量化和便捷方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * 单个文本向量化
     *
     * @param text 输入文本
     * @return 1024 维浮点数组
     */
    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(text).content();
        return embedding.vector();
    }

    /**
     * 批量向量化（逐条调用，业务量不大时足够）
     *
     * @param texts 输入文本列表
     * @return 向量列表，与输入一一对应
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            try {
                vectors.add(embed(texts.get(i)));
            } catch (Exception e) {
                log.error("批量向量化失败 [index={}]: {}", i, e.getMessage());
                throw new RuntimeException("向量化失败 at index " + i, e);
            }
        }
        return vectors;
    }

    /**
     * 对 TextSegment 列表做向量化，返回 (TextSegment, float[]) 配对列表
     */
    public List<SegmentVectorPair> embedSegments(List<TextSegment> segments) {
        List<SegmentVectorPair> pairs = new ArrayList<>(segments.size());
        for (TextSegment seg : segments) {
            float[] vector = embed(seg.text());
            pairs.add(new SegmentVectorPair(seg, vector));
        }
        return pairs;
    }

    /**
     * 获取向量维度
     */
    public int dimension() {
        return embed("test").length;
    }

    // ===== 内部类 =====

    public record SegmentVectorPair(TextSegment segment, float[] vector) {}
}
