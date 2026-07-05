package com.example.server.rag.ingestion;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 递归文本分块器
 *
 * 按分隔符优先级逐级拆分：
 *   段落(\\n\\n) → 换行(\\n) → 句子(。) → 子句(，) → 空格( ) → 字符级截断
 *
 * 核心参数：
 *   chunkSize: 目标块大小（字符数），默认 800
 *   overlap:   相邻块重叠字符数，默认 100
 *
 * 输出 LangChain4j TextSegment，每个携带 Metadata（chunkIndex、sourceTitle 等）
 */
@Slf4j
@Component
public class RecursiveTextSplitter {

    /** 分隔符优先级：从粗到细 */
    private static final String[] DEFAULT_SEPARATORS = {
            "\n\n",     // 段落
            "\n",       // 换行
            "。",       // 中文句号
            "！",       // 中文感叹号
            "？",       // 中文问号
            "；",       // 中文分号
            "，",       // 中文逗号
            " ",        // 空格
            ""          // 字符级截断（最后兜底）
    };

    @Value("${rag.chunk.size:800}")
    private int chunkSize;

    @Value("${rag.chunk.overlap:100}")
    private int overlap;

    // ==================== 公开 API ====================

    /**
     * 拆分文本为 TextSegment 列表
     *
     * @param text         原始文本
     * @param baseMetadata 基础元数据（title, source, timestamp 等）
     */
    public List<TextSegment> split(String text, Map<String, Object> baseMetadata) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 第1步：递归切分
        List<String> splits = new ArrayList<>();
        recursiveSplit(text.trim(), DEFAULT_SEPARATORS, 0, splits);

        // 第2步：合并小片段 + 重叠
        List<String> chunks = mergeSplits(splits);

        // 第3步：包装为 TextSegment
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Metadata meta = buildMetadata(baseMetadata, i, chunks.size());
            segments.add(TextSegment.from(chunks.get(i), meta));
        }

        log.info("文本分块完成: {} 字符 → {} chunks (chunkSize={}, overlap={})",
                text.length(), segments.size(), chunkSize, overlap);
        return segments;
    }

    // ==================== 递归切分 ====================

    /**
     * 递归切分核心：从 sepIndex 对应的分隔符开始尝试
     * 如果某个片段仍然超过 chunkSize，换下一个（更细的）分隔符
     */
    private void recursiveSplit(String text, String[] separators, int sepIndex, List<String> result) {
        if (text.length() <= chunkSize) {
            if (!text.trim().isEmpty()) {
                result.add(text.trim());
            }
            return;
        }

        // 所有分隔符都用过了，只能暴力截断
        if (sepIndex >= separators.length) {
            forceSplit(text, result);
            return;
        }

        String separator = separators[sepIndex];

        // 空字符串分隔符 = 字符级强制截断
        if (separator.isEmpty()) {
            forceSplit(text, result);
            return;
        }

        // 用当前分隔符拆分
        String[] parts = text.split(Pattern.quote(separator), -1);

        for (String part : parts) {
            if (part.trim().isEmpty()) {
                continue;
            }
            if (part.length() > chunkSize) {
                // 太长的片段用下一级分隔符继续拆
                recursiveSplit(part, separators, sepIndex + 1, result);
            } else {
                result.add(part.trim());
            }
        }
    }

    /**
     * 暴力截断：按 chunkSize 直接切开
     */
    private void forceSplit(String text, List<String> result) {
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end).trim();
            if (!chunk.isEmpty()) {
                result.add(chunk);
            }
        }
    }

    // ==================== 合并 + 重叠 ====================

    /**
     * 将小片段合并成接近 chunkSize 的块
     * 相邻块之间保留 overlap 个字符的重叠区
     */
    private List<String> mergeSplits(List<String> splits) {
        if (splits.isEmpty()) {
            return splits;
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // 记录当前块末尾用于重叠的文本
        String overlapBuffer = "";

        for (String split : splits) {
            String toAdd = split;

            // 如果当前块已有内容，且加上新片段后超过 chunkSize，则收口
            if (current.length() + toAdd.length() > chunkSize && current.length() > 0) {
                // 加上重叠区的前缀（）
                chunks.add(current.toString().trim());
                // 新块从 overlap 开始
                if (overlap > 0 && current.length() > overlap) {
                    overlapBuffer = current.substring(current.length() - overlap);
                    current = new StringBuilder(overlapBuffer);
                } else {
                    current = new StringBuilder();
                }
            }

            if (current.length() > 0) {
                current.append("\n");
            }
            current.append(toAdd);
        }

        // 最后一个块
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    // ==================== 元数据构建 ====================

    private Metadata buildMetadata(Map<String, Object> base, int chunkIndex, int totalChunks) {
        Metadata meta = new Metadata();
        if (base != null) {
            for (Map.Entry<String, Object> entry : base.entrySet()) {
                meta.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }
        meta.put("chunkIndex", String.valueOf(chunkIndex));
        meta.put("totalChunks", String.valueOf(totalChunks));
        meta.put("createdAt", String.valueOf(System.currentTimeMillis()));
        return meta;
    }
}
