package com.example.server.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM 模型配置
 * 复用现有 DeepSeek API，通过 OpenAI 兼容协议调用
 * 同时提供同步模型（普通问答）和流式模型（SSE 输出）
 */
@Slf4j
@Configuration
public class RagConfig {

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    /**
     * 同步 ChatModel —— 普通问答、非流式场景
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("初始化 DeepSeek ChatModel: {}", baseUrl);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("deepseek-chat")
                .temperature(0.3)        // 适中温度，保留准确性同时有一定灵活性
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .logRequests(true)
                .logResponses(false)
                .build();
    }

    /**
     * 流式 ChatModel —— SSE 流式输出场景
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        log.info("初始化 DeepSeek StreamingChatModel: {}", baseUrl);

        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("deepseek-chat")
                .temperature(0.5)        // 流式对话稍高温度，回答更自然
                .timeout(Duration.ofSeconds(120))
                .logRequests(true)
                .logResponses(false)
                .build();
    }
}
