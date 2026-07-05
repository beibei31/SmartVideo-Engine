package com.example.server.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 检索结果模型
 * 包含一次检索命中的 chunk 及其相关性分数、来源信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /** chunk 唯一标识 */
    private String chunkId;

    /** chunk 文本内容 */
    private String content;

    /** 相关性分数：余弦相似度 [0, 1]，或 BM25 分数 [0, +∞) */
    private double score;

    /** 来源标题 */
    private String sourceTitle;

    /** chunk 在原文中的序号 */
    private int chunkIndex;

    /** 检索方式：DENSE / SPARSE / HYBRID */
    private String retrievalType;

    /** 扩展元数据 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // ===== 便捷方法 =====

    /** 是否通过阈值过滤 */
    public boolean passesThreshold(double threshold) {
        return score >= threshold;
    }

    /** 格式化展示 */
    public String toDisplayText() {
        return String.format("[%s] chunk_%03d  [%.2f]  \"%s...\"",
                sourceTitle != null ? sourceTitle : "unknown",
                chunkIndex,
                score,
                content != null && content.length() > 80
                        ? content.substring(0, 80)
                        : content);
    }
}
