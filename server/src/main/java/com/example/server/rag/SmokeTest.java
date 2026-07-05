package com.example.server.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 连通性测试：验证 Embedding 模型 + Milvus 向量数据库是否正常工作
 * 启动时自动执行，通过后打印 ✅，失败不影响应用启动
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.smoke-test", name = "enabled", havingValue = "true")
public class SmokeTest implements CommandLineRunner {

    private final EmbeddingModel embeddingModel;
    private final MilvusEmbeddingStore milvusEmbeddingStore;

    @Value("${milvus.collection-name}")
    private String collectionName;

    private static final String TEST_TEXT = "这是一条连通性测试文本，用于验证RAG基础设施是否正常工作。";

    @Override
    public void run(String... args) {
        try {
            log.info("============================================");
            log.info("  RAG 基础设施连通性测试 (SmokeTest)");
            log.info("============================================");

            // Step 1: 测试 Embedding 模型
            log.info("[1/3] 测试 Embedding 模型...");
            Embedding embedding = embeddingModel.embed(TEST_TEXT).content();
            log.info("  ✅ Embedding 成功，向量维度: {}", embedding.vector().length);

            if (embedding.vector().length != 1024) {
                log.warn("  ⚠️ 向量维度为 {}，预期 1024（BGE-Large-ZH）", embedding.vector().length);
            }

            // Step 2: 测试 Milvus 写入
            log.info("[2/3] 测试 Milvus 向量写入...");
            TextSegment segment = TextSegment.from(TEST_TEXT);
            // add 返回生成的 ID
            String insertedId = milvusEmbeddingStore.add(embedding, segment);
            log.info("  ✅ 向量写入成功，Collection: {}, ID: {}", collectionName, insertedId);

            // Step 3: 测试 Milvus 检索
            log.info("[3/3] 测试 Milvus 向量检索...");
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(3)
                    .minScore(0.6)
                    .build();
            List<EmbeddingMatch<TextSegment>> results =
                    milvusEmbeddingStore.search(searchRequest).matches();

            if (!results.isEmpty()) {
                log.info("  ✅ 检索成功，返回 {} 条结果", results.size());
                for (EmbeddingMatch<TextSegment> match : results) {
                    String snippet = match.embedded().text();
                    if (snippet.length() > 50) {
                        snippet = snippet.substring(0, 50) + "...";
                    }
                    log.info("     - score: {:.4f}, text: {}", match.score(), snippet);
                }
            } else {
                log.warn("  ⚠️ 检索无结果（阈值 0.6 可能过滤了结果）");
            }

            // 清理测试数据
            milvusEmbeddingStore.remove(insertedId);
            log.info("  测试数据已清理 (ID: {})", insertedId);

            log.info("============================================");
            log.info("  ✅ RAG 基础设施全部连通！");
            log.info("============================================");

        } catch (Exception e) {
            log.error("============================================");
            log.error("  ❌ RAG 基础设施连通性测试失败！");
            log.error("  请检查: (1) Milvus 容器是否启动 (docker ps)");
            log.error("         (2) Embedding 模型服务是否可用");
            log.error("         (3) application.properties 配置是否正确");
            log.error("============================================");
            log.error("异常详情:", e);
        }
    }
}
