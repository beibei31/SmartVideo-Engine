package com.example.server.rag.retrieval;

import com.example.server.rag.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 内存 BM25 关键词检索索引
 *
 * 为什么不用 Milvus Sparse Vector：
 *   Milvus 2.4+ 虽然原生支持 BM25，但需要通过 Sparse Vector Field +
 *   BM25 Embedding Function，与 LangChain4j 的 MilvusEmbeddingStore
 *   抽象层不兼容。直接用 Milvus SDK 裸调改动太大。
 *
 * 内存方案的取舍：
 *   ✅ 零依赖、零配置、即插即用
 *   ✅ 千~万级 chunk 毫秒级响应
 *   ✅ 与现有 RRF 融合链路无缝对接（通过 chunkId 关联）
 *   ⚠️ 不持久化（重启后索引需重建，但 IngestionService 入库时会同步写）
 *   ⚠️ 百万级以上需换 Lucene 或 Milvus Sparse Vector
 *
 * BM25 公式：
 *   Score(q,d) = Σ IDF(qi) * tf * (k1+1) / (tf + k1*(1-b + b*dl/avgdl))
 *
 *   中文分词策略：Unigram + Bigram（无需外部分词器）
 */
@Slf4j
@Component
public class Bm25Index {

    // ==================== 索引数据结构 ====================

    /** chunkId → (term → frequency) */
    private final Map<String, Map<String, Integer>> termFreqIndex = new ConcurrentHashMap<>();

    /** term → document frequency（包含该词的文档数） */
    private final Map<String, Integer> docFreq = new ConcurrentHashMap<>();

    /** chunkId → document length（token 数） */
    private final Map<String, Integer> docLengths = new ConcurrentHashMap<>();

    /** chunkId → content（用于构造 SearchResult） */
    private final Map<String, String> contents = new ConcurrentHashMap<>();

    /** chunkId → sourceTitle */
    private final Map<String, String> sourceTitles = new ConcurrentHashMap<>();

    /** chunkId → chunkIndex */
    private final Map<String, Integer> chunkIndexes = new ConcurrentHashMap<>();

    /** 总文档数 */
    private volatile int totalDocs = 0;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ==================== BM25 参数 ====================

    /** 词频饱和度控制（值越大，词频影响越小） */
    private static final double K1 = 1.5;

    /** 文档长度归一化（0=不考虑长度，1=完全归一化） */
    private static final double B = 0.75;

    // ==================== 公开 API ====================

    /**
     * 将 chunk 加入索引
     *
     * @param chunkId     Milvus 返回的 embedding ID
     * @param content     文本内容
     * @param sourceTitle 来源标题
     * @param chunkIndex  分块序号
     */
    public void index(String chunkId, String content, String sourceTitle, int chunkIndex) {
        if (content == null || content.isBlank()) return;

        List<String> tokens = tokenize(content);

        lock.writeLock().lock();
        try {
            // 如果该 chunk 已存在，先移除旧的
            removeInternal(chunkId);

            // 计算词频
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokens) {
                tf.merge(t, 1, Integer::sum);
            }

            termFreqIndex.put(chunkId, tf);
            docLengths.put(chunkId, tokens.size());
            contents.put(chunkId, content);
            sourceTitles.put(chunkId, sourceTitle);
            chunkIndexes.put(chunkId, chunkIndex);
            totalDocs++;

            // 更新文档频率
            for (String term : tf.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
        } finally {
            lock.writeLock().unlock();
        }

        log.debug("BM25 索引: chunkId={}, tokens={}, totalDocs={}", chunkId, tokens.size(), totalDocs);
    }

    /**
     * 批量添加（减少锁竞争）
     */
    public void indexBatch(Map<String, IndexEntry> entries) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, IndexEntry> e : entries.entrySet()) {
                String chunkId = e.getKey();
                IndexEntry entry = e.getValue();

                removeInternal(chunkId);

                List<String> tokens = tokenize(entry.content);
                Map<String, Integer> tf = new HashMap<>();
                for (String t : tokens) {
                    tf.merge(t, 1, Integer::sum);
                }

                termFreqIndex.put(chunkId, tf);
                docLengths.put(chunkId, tokens.size());
                contents.put(chunkId, entry.content);
                sourceTitles.put(chunkId, entry.sourceTitle);
                chunkIndexes.put(chunkId, entry.chunkIndex);
                totalDocs++;

                for (String term : tf.keySet()) {
                    docFreq.merge(term, 1, Integer::sum);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * BM25 关键词检索
     *
     * @param query 查询文本
     * @param topK  返回数量
     * @return 按 BM25 分数降序的检索结果
     */
    public List<SearchResult> search(String query, int topK) {
        if (totalDocs == 0 || query == null || query.isBlank()) {
            return List.of();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        // 去重但保留查询词频
        Map<String, Integer> queryTf = new HashMap<>();
        for (String t : queryTokens) {
            queryTf.merge(t, 1, Integer::sum);
        }

        // 计算平均文档长度
        double avgdl;
        lock.readLock().lock();
        try {
            avgdl = docLengths.values().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(1.0);

            int N = totalDocs;

            // 对每个文档打分
            List<ScoredDoc> scored = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> entry : termFreqIndex.entrySet()) {
                String chunkId = entry.getKey();
                Map<String, Integer> tf = entry.getValue();
                int docLen = docLengths.getOrDefault(chunkId, 1);

                double score = 0.0;
                for (Map.Entry<String, Integer> qt : queryTf.entrySet()) {
                    String term = qt.getKey();
                    int qf = qt.getValue();      // query term frequency
                    int f = tf.getOrDefault(term, 0);  // doc term frequency
                    if (f == 0) continue;

                    int df = docFreq.getOrDefault(term, 0);
                    // IDF: 稀有词权重高，常见词权重低
                    double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1.0);
                    // TF 归一化
                    double tfNorm = (f * (K1 + 1)) / (f + K1 * (1 - B + B * docLen / avgdl));

                    score += idf * tfNorm * qf;
                }

                if (score > 0) {
                    scored.add(new ScoredDoc(chunkId, score));
                }
            }

            // 按分数降序排列，取 Top-K
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            int limit = Math.min(topK, scored.size());

            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                ScoredDoc doc = scored.get(i);
                results.add(SearchResult.builder()
                        .chunkId(doc.chunkId)
                        .content(contents.get(doc.chunkId))
                        .score(doc.score)
                        .sourceTitle(sourceTitles.get(doc.chunkId))
                        .chunkIndex(chunkIndexes.get(doc.chunkId))
                        .retrievalType("SPARSE")
                        .build());
            }

            log.debug("BM25 检索: query='{}', 召回 {} / {} 条",
                    query.length() > 30 ? query.substring(0, 30) + "..." : query,
                    results.size(), totalDocs);

            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 移除单个 chunk
     */
    public void remove(String chunkId) {
        lock.writeLock().lock();
        try {
            removeInternal(chunkId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清空全部索引
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            termFreqIndex.clear();
            docFreq.clear();
            docLengths.clear();
            contents.clear();
            sourceTitles.clear();
            chunkIndexes.clear();
            totalDocs = 0;
            log.info("BM25 索引已清空");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        return totalDocs;
    }

    // ==================== 中文 + 英文混合分词 ====================

    /**
     * 分词策略：
     *   - 中文字符 → Unigram（单字）+ Bigram（双字组合）
     *   - 英文/ASCII → 小写化后按空格分词
     *   - 数字 → 整体保留
     *   - 标点/空白 → 丢弃
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();

        // 按 Unicode 块切分：连续中文 / 连续英文数字 / 其它
        StringBuilder buf = new StringBuilder();
        CharType currentType = CharType.OTHER;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            CharType type = classify(c);

            if (type != currentType && buf.length() > 0) {
                addTokens(buf.toString(), currentType, tokens);
                buf.setLength(0);
            }
            currentType = type;

            // 忽略空白和标点
            if (type != CharType.SPACE && type != CharType.PUNCT) {
                buf.append(c);
                // 中文逐字处理，不拼 buffer
                if (type == CharType.CJK) {
                    // 单字直接作为 unigram
                    // bigram 由跨字符处理
                }
            }
        }
        if (buf.length() > 0) {
            addTokens(buf.toString(), currentType, tokens);
        }

        return tokens;
    }

    private void addTokens(String segment, CharType type, List<String> tokens) {
        switch (type) {
            case CJK -> {
                // Unigrams：每个字都作为一个 token
                for (int i = 0; i < segment.length(); i++) {
                    tokens.add(String.valueOf(segment.charAt(i)));
                }
                // Bigrams：相邻两字组合
                for (int i = 0; i < segment.length() - 1; i++) {
                    tokens.add(segment.substring(i, i + 2));
                }
                // Trigram（可选，较长词更精准）
                if (segment.length() >= 3) {
                    for (int i = 0; i < segment.length() - 2; i++) {
                        tokens.add(segment.substring(i, i + 3));
                    }
                }
            }
            case ASCII -> {
                // 小写化，按空格和标点拆分
                String lower = segment.toLowerCase().trim();
                if (!lower.isEmpty()) {
                    // 拆分英文中的标点分隔符
                    String[] words = lower.split("[^a-z0-9]+");
                    for (String w : words) {
                        if (!w.isEmpty()) {
                            tokens.add(w);
                        }
                    }
                }
            }
            case NUMBER -> tokens.add(segment);
            // SPACE / PUNCT / OTHER → 直接丢弃
        }
    }

    private CharType classify(char c) {
        if (Character.isWhitespace(c)) return CharType.SPACE;
        if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
            // CJK 标点归为 PUNCT
            if (c == '。' || c == '，' || c == '、' || c == '；' || c == '：'
                    || c == '？' || c == '！' || c == '「' || c == '」'
                    || c == '『' || c == '』' || c == '（' || c == '）'
                    || c == '《' || c == '》') {
                return CharType.PUNCT;
            }
            return CharType.CJK;
        }
        if (c >= 0x4E00 && c <= 0x9FFF) return CharType.CJK;   // CJK 统一汉字
        if (c >= 0x3400 && c <= 0x4DBF) return CharType.CJK;   // CJK 扩展A
        if (c >= 0xF900 && c <= 0xFAFF) return CharType.CJK;   // CJK 兼容汉字
        if (Character.isDigit(c)) return CharType.NUMBER;
        if (Character.isLetter(c)) return CharType.ASCII;
        if (c == '-' || c == '_' || c == '@' || c == '.' || c == '+' || c == '#') return CharType.ASCII;
        return CharType.PUNCT;
    }

    // ==================== 内部方法 ====================

    private void removeInternal(String chunkId) {
        Map<String, Integer> oldTf = termFreqIndex.remove(chunkId);
        if (oldTf != null) {
            for (String term : oldTf.keySet()) {
                int newDf = docFreq.getOrDefault(term, 1) - 1;
                if (newDf <= 0) {
                    docFreq.remove(term);
                } else {
                    docFreq.put(term, newDf);
                }
            }
            docLengths.remove(chunkId);
            contents.remove(chunkId);
            sourceTitles.remove(chunkId);
            chunkIndexes.remove(chunkId);
            totalDocs--;
        }
    }

    // ==================== 内部类型 ====================

    private enum CharType {
        CJK,     // 中日韩文字
        ASCII,   // 英文/字母
        NUMBER,  // 数字
        SPACE,   // 空白
        PUNCT,   // 标点
        OTHER    // 其它
    }

    private record ScoredDoc(String chunkId, double score) {}

    /**
     * 批量索引条目
     */
    public record IndexEntry(String content, String sourceTitle, int chunkIndex) {}
}
