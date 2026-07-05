package com.example.server.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识块文档模型
 * 对应 Milvus 中存储的每个 chunk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDocument {

    /** 唯一标识，Milvus 自动生成 */
    private String id;

    /** 文本内容 */
    private String content;

    /** chunk 在原文中的序号（从 0 开始） */
    private int chunkIndex;

    /** 来源标题（如视频文件名、文章标题） */
    private String sourceTitle;

    /** 来源类型：ASR转写 / Markdown / PDF / 纯文本 */
    private String sourceType;

    /** 创建时间戳 */
    private long timestamp;

    /** 扩展元数据 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // ===== 便捷方法 =====

    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
}
