package com.example.server.chat.controller;

import com.example.server.chat.generation.GenerationService;
import com.example.server.chat.memory.ChatMemoryService;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.rag.ingestion.IngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final GenerationService generationService;
    private final ChatMemoryService chatMemoryService;
    private final IngestionService ingestionService;
    private final MediaFileMapper mediaFileMapper;

    public RagController(GenerationService generationService,
                         ChatMemoryService chatMemoryService,
                         IngestionService ingestionService,
                         MediaFileMapper mediaFileMapper) {
        this.generationService = generationService;
        this.chatMemoryService = chatMemoryService;
        this.ingestionService = ingestionService;
        this.mediaFileMapper = mediaFileMapper;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> request) {
        String question = getString(request, "question");
        String sessionId = getString(request, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        Long videoId = getLong(request, "videoId");

        if (question == null || question.isBlank()) {
            SseEmitter emitter = new SseEmitter(5000L);
            try {
                emitter.send(SseEmitter.event().name("error").data("问题不能为空"));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        log.info("SSE RAG request: session={}, question={}",
                sessionId,
                question.length() > 50 ? question.substring(0, 50) + "..." : question);

        SseEmitter emitter = new SseEmitter(300_000L);
        generationService.generateStreaming(question, sessionId, videoId, emitter);
        String finalSessionId = sessionId;
        emitter.onCompletion(() -> log.info("SSE complete: session={}", finalSessionId));
        emitter.onTimeout(() -> log.warn("SSE timeout: session={}", finalSessionId));
        return emitter;
    }

    @PostMapping("/chat/sync")
    public Map<String, Object> chatSync(@RequestBody Map<String, Object> request) {
        String question = getString(request, "question");
        String sessionId = getString(request, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "debug";
        }
        Long videoId = getLong(request, "videoId");

        if (question == null || question.isBlank()) {
            return Map.of("error", "问题不能为空");
        }

        GenerationService.RagResult result = generationService.generateWithContexts(question, sessionId, videoId);
        var contextsJson = result.contexts().stream()
                .map(ctx -> Map.of(
                        "content", ctx.getContent() != null ? ctx.getContent() : "",
                        "score", ctx.getScore(),
                        "sourceTitle", ctx.getSourceTitle() != null ? ctx.getSourceTitle() : "",
                        "chunkIndex", ctx.getChunkIndex()
                ))
                .toList();

        return Map.of(
                "sessionId", sessionId,
                "question", question,
                "answer", result.answer(),
                "contexts", contextsJson
        );
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String title = request.getOrDefault("title", "未命名");
        String type = request.getOrDefault("type", "ASR转写");

        if (text == null || text.isBlank()) {
            return Map.of("error", "文本不能为空");
        }

        int count = ingestionService.ingestFromText(text, title, type);
        return Map.of(
                "title", title,
                "type", type,
                "chunkCount", count,
                "status", "ingested"
        );
    }

    @PostMapping("/ingest/{mediaId}")
    public Map<String, Object> ingestByMediaId(@PathVariable Long mediaId) {
        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) {
            return Map.of("error", "mediaId 不存在: " + mediaId);
        }

        String text = mediaFile.getTranscriptText();
        if (text == null || text.isBlank()) {
            return Map.of("error", "该视频尚未完成 ASR 转写: mediaId=" + mediaId);
        }

        String title = mediaFile.getFilename() != null ? mediaFile.getFilename() : "video-" + mediaId;
        int count = ingestionService.ingestFromText(mediaId, text, title, "ASR转写");
        return Map.of(
                "mediaId", mediaId,
                "title", title,
                "textLength", text.length(),
                "chunkCount", count,
                "status", "ingested"
        );
    }

    @DeleteMapping("/memory/{sessionId}")
    public Map<String, Object> clearMemory(@PathVariable String sessionId) {
        chatMemoryService.clear(sessionId);
        log.info("RAG memory cleared: session={}", sessionId);
        return Map.of(
                "sessionId", sessionId,
                "status", "cleared"
        );
    }

    private String getString(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private Long getLong(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        return Long.parseLong(text);
    }
}
