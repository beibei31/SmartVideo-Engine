package com.example.server.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Embedding 模型配置
 * 使用 Ollama 本地运行 BGE-Large-ZH，输出 1024 维归一化向量
 *
 * 前置条件: ollama pull bge-large
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.model-name}")
    private String modelName;

    /**
     * 创建 OllamaEmbeddingModel Bean
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 Ollama Embedding 模型: {} @ {}", modelName, baseUrl);

        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
