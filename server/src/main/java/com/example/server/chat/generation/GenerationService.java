package com.example.server.chat.generation;

import com.example.server.chat.memory.ChatMemoryService;
import com.example.server.chat.prompt.PromptBuilder;
import com.example.server.rag.model.RetrievalRequest;
import com.example.server.rag.model.SearchResult;
import com.example.server.rag.retrieval.RetrievalService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class GenerationService {

    private static final String NO_CONTEXT_ANSWER =
            "抱歉，根据现有资料，我无法回答这个问题。请确认知识库中是否包含相关信息。";

    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ChatMemoryService chatMemoryService;

    @Value("${rag.retrieval.hybrid-enabled:false}")
    private boolean enableHybridRetrieval;

    public GenerationService(RetrievalService retrievalService,
                             PromptBuilder promptBuilder,
                             ChatLanguageModel chatLanguageModel,
                             StreamingChatLanguageModel streamingChatLanguageModel,
                             ChatMemoryService chatMemoryService) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.chatMemoryService = chatMemoryService;
    }

    public String generate(String question) {
        return generate(question, null);
    }

    public String generate(String question, String sessionId) {
        return generateWithContexts(question, sessionId).answer();
    }

    public RagResult generateWithContexts(String question) {
        return generateWithContexts(question, null);
    }

    public RagResult generateWithContexts(String question, String sessionId) {
        List<String> history = loadHistory(sessionId);
        List<SearchResult> contexts = retrievalService.search(question, enableHybridRetrieval, history);

        if (contexts.isEmpty()) {
            return new RagResult(NO_CONTEXT_ANSWER, List.of());
        }

        String prompt = promptBuilder.build(question, contexts, history);
        String answer = chatLanguageModel.chat(prompt);
        saveMemory(sessionId, question, answer);

        log.info("Sync RAG generation complete: questionLength={}, contexts={}, answerLength={}",
                question.length(), contexts.size(), answer != null ? answer.length() : 0);
        return new RagResult(answer, contexts);
    }

    public RagResult generateWithContexts(String question, String sessionId, Long videoId) {
        List<String> history = loadHistory(sessionId);
        List<SearchResult> contexts = retrievalService.search(RetrievalRequest.builder()
                .question(question)
                .videoId(videoId)
                .hybrid(enableHybridRetrieval)
                .history(history)
                .build());

        if (contexts.isEmpty()) {
            return new RagResult(NO_CONTEXT_ANSWER, List.of());
        }

        String prompt = promptBuilder.build(question, contexts, history);
        String answer = chatLanguageModel.chat(prompt);
        saveMemory(sessionId, question, answer);

        log.info("Scoped sync RAG generation complete: questionLength={}, videoId={}, contexts={}, answerLength={}",
                question.length(), videoId, contexts.size(), answer != null ? answer.length() : 0);
        return new RagResult(answer, contexts);
    }

    public record RagResult(String answer, List<SearchResult> contexts) {}

    public void generateStreaming(String question, SseEmitter emitter) {
        generateStreaming(question, null, emitter);
    }

    public void generateStreaming(String question, String sessionId, SseEmitter emitter) {
        generateStreaming(question, sessionId, null, emitter);
    }

    public void generateStreaming(String question, String sessionId, Long videoId, SseEmitter emitter) {
        sendSseEvent(emitter, "status", "正在检索相关知识...");

        List<String> history = loadHistory(sessionId);
        List<SearchResult> contexts;
        try {
            if (videoId != null) {
                contexts = retrievalService.search(RetrievalRequest.builder()
                        .question(question)
                        .videoId(videoId)
                        .hybrid(enableHybridRetrieval)
                        .history(history)
                        .build());
            } else {
                contexts = retrievalService.search(question, enableHybridRetrieval, history);
            }
        } catch (Exception e) {
            log.error("RAG retrieval failed", e);
            sendSseEvent(emitter, "error", "检索失败: " + e.getMessage());
            emitter.complete();
            return;
        }

        if (contexts.isEmpty()) {
            sendSseEvent(emitter, "message", NO_CONTEXT_ANSWER);
            sendSseEvent(emitter, "contexts", toJsonString(contexts));
            sendSseEvent(emitter, "done", "");
            emitter.complete();
            return;
        }

        sendSseEvent(emitter, "contexts", toJsonString(contexts));

        String prompt = promptBuilder.build(question, contexts, history);
        sendSseEvent(emitter, "status", "正在生成回答...");
        log.info("Streaming RAG generation started: promptLength={}, contexts={}",
                prompt.length(), contexts.size());

        CompletableFuture.runAsync(() -> streamAnswer(question, sessionId, emitter, contexts, prompt))
                .orTimeout(110, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.error("Streaming RAG generation failed or timed out", ex);
                    sendSseEvent(emitter, "error", "回答生成超时，请重试");
                    emitter.complete();
                    return null;
                });
    }

    private void streamAnswer(String question,
                              String sessionId,
                              SseEmitter emitter,
                              List<SearchResult> contexts,
                              String prompt) {
        try {
            streamingChatLanguageModel.chat(prompt, new StreamingChatResponseHandler() {

                private final AtomicBoolean firstToken = new AtomicBoolean(false);
                private final StringBuilder answerBuffer = new StringBuilder();

                @Override
                public void onPartialResponse(String token) {
                    if (firstToken.compareAndSet(false, true)) {
                        log.info("Streaming RAG received first token: length={}", token.length());
                    }
                    answerBuffer.append(token);
                    sendSseEvent(emitter, "message", token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    String answer = answerBuffer.toString();
                    if (answer.isBlank() && response != null && response.aiMessage() != null) {
                        answer = response.aiMessage().text();
                    }
                    saveMemory(sessionId, question, answer);
                    sendSseEvent(emitter, "done", "");
                    emitter.complete();
                    log.info("Streaming RAG generation complete: contexts={}, answerLength={}",
                            contexts.size(), answer.length());
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Streaming RAG generation error", error);
                    sendSseEvent(emitter, "error", "生成回答时出错: " + error.getMessage());
                    emitter.completeWithError(error);
                }
            });
        } catch (Exception e) {
            log.error("Streaming RAG invocation failed", e);
            sendSseEvent(emitter, "error", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private List<String> loadHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return chatMemoryService.loadHistory(sessionId);
    }

    private void saveMemory(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isBlank() || answer == null || answer.isBlank()) {
            return;
        }
        chatMemoryService.save(sessionId, question, answer);
    }

    private void sendSseEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            log.debug("SSE send failed, client may have disconnected: {}", e.getMessage());
        }
    }

    private String toJsonString(List<SearchResult> contexts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < contexts.size(); i++) {
            SearchResult c = contexts.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            sb.append("\"index\":").append(i + 1).append(",");
            sb.append("\"score\":").append(String.format("%.4f", c.getScore())).append(",");
            sb.append("\"retrievalType\":\"")
                    .append(c.getRetrievalType() != null ? c.getRetrievalType() : "DENSE")
                    .append("\",");
            sb.append("\"sourceTitle\":\"").append(escapeJson(c.getSourceTitle())).append("\",");
            sb.append("\"content\":\"").append(escapeJson(shorten(c.getContent()))).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String shorten(String content) {
        if (content == null || content.length() <= 80) {
            return content;
        }
        return content.substring(0, 80) + "...";
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
