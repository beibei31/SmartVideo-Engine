package com.example.server.rag.config;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 * 使用 LangChain4j 的 MilvusEmbeddingStore，底层自动管理
 * Collection 创建、HNSW 索引、稠密向量 + 稀疏向量存储
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.dimension}")
    private int dimension;

    /**
     * 创建 MilvusEmbeddingStore Bean
     * 连接时自动检查 Collection 是否存在，不存在则创建
     */
    @Bean
    public MilvusEmbeddingStore milvusEmbeddingStore() {
        log.info("正在连接 Milvus: {}:{}, Collection={}, Dimension={}",
                host, port, collectionName, dimension);

        MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collectionName)
                .dimension(dimension)
                .build();

        log.info("Milvus 连接成功, Collection: {}", collectionName);
        return store;
    }
}
