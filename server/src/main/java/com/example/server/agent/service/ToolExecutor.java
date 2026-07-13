package com.example.server.agent.service;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.KnowledgeQaInput;
import com.example.server.agent.model.QuizInput;
import com.example.server.agent.model.ToolCall;
import com.example.server.agent.model.ToolResult;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSegmentLocatorInput;
import com.example.server.agent.model.VideoSummaryInput;
import com.example.server.agent.tool.KnowledgeQaTool;
import com.example.server.agent.tool.QuizTool;
import com.example.server.agent.tool.VideoSegmentLocatorTool;
import com.example.server.agent.tool.VideoSearchTool;
import com.example.server.agent.tool.VideoSummaryTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class ToolExecutor {

    private final VideoSearchTool videoSearchTool;
    private final KnowledgeQaTool knowledgeQaTool;
    private final VideoSegmentLocatorTool videoSegmentLocatorTool;
    private final VideoSummaryTool videoSummaryTool;
    private final QuizTool quizTool;
    private final long timeoutMs;
    private final Executor toolExecutor;

    public ToolExecutor(VideoSearchTool videoSearchTool,
                        KnowledgeQaTool knowledgeQaTool,
                        VideoSegmentLocatorTool videoSegmentLocatorTool,
                        VideoSummaryTool videoSummaryTool,
                        QuizTool quizTool) {
        this(videoSearchTool, knowledgeQaTool, videoSegmentLocatorTool, videoSummaryTool, quizTool,
                30_000L, CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
    }

    @Autowired
    public ToolExecutor(VideoSearchTool videoSearchTool,
                        KnowledgeQaTool knowledgeQaTool,
                        VideoSegmentLocatorTool videoSegmentLocatorTool,
                        VideoSummaryTool videoSummaryTool,
                        QuizTool quizTool,
                        @Value("${agent.tool.timeout-ms:30000}") long timeoutMs,
                        @Qualifier("aiTaskExecutor") Executor toolExecutor) {
        this.videoSearchTool = videoSearchTool;
        this.knowledgeQaTool = knowledgeQaTool;
        this.videoSegmentLocatorTool = videoSegmentLocatorTool;
        this.videoSummaryTool = videoSummaryTool;
        this.quizTool = quizTool;
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.toolExecutor = toolExecutor;
    }

    public ToolResult execute(ToolCall call, AgentState state) {
        try {
            if (call.arguments() == null) {
                throw new IllegalArgumentException("arguments must not be null");
            }
            Supplier<ToolResult> invocation = () -> switch (call.action()) {
                case "VideoSearchTool" -> ToolResult.success(
                        videoSearchTool.name(),
                        videoSearchTool.execute(toVideoSearchInput(call.arguments()), state)
                );
                case "KnowledgeQaTool" -> ToolResult.success(
                        knowledgeQaTool.name(),
                        knowledgeQaTool.execute(toKnowledgeQaInput(call.arguments()), state)
                );
                case "VideoSegmentLocatorTool" -> ToolResult.success(
                        videoSegmentLocatorTool.name(),
                        videoSegmentLocatorTool.execute(toVideoSegmentLocatorInput(call.arguments()), state)
                );
                case "VideoSummaryTool" -> ToolResult.success(
                        videoSummaryTool.name(),
                        videoSummaryTool.execute(toVideoSummaryInput(call.arguments()), state)
                );
                case "QuizTool" -> ToolResult.success(
                        quizTool.name(),
                        quizTool.execute(toQuizInput(call.arguments()), state)
                );
                default -> ToolResult.failure(call.action(), "Unknown tool action: " + call.action());
            };
            CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(invocation, toolExecutor);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(call.action(), "Invalid tool arguments: " + e.getMessage());
        } catch (TimeoutException e) {
            return ToolResult.failure(call.action(), "Tool execution timed out after " + timeoutMs + "ms");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException argumentException) {
                return ToolResult.failure(call.action(), "Invalid tool arguments: " + argumentException.getMessage());
            }
            return ToolResult.failure(call.action(), "Tool execution failed: " + e.getCause().getMessage());
        } catch (Exception e) {
            return ToolResult.failure(call.action(), "Tool execution failed: " + e.getMessage());
        }
    }

    private VideoSearchInput toVideoSearchInput(Map<String, Object> arguments) {
        return new VideoSearchInput(
                stringValue(arguments.get("query")),
                longValue(arguments.get("videoId"), "videoId"),
                intValue(arguments.get("topK"), 5, "topK")
        );
    }

    private KnowledgeQaInput toKnowledgeQaInput(Map<String, Object> arguments) {
        String question = stringValue(arguments.get("question"));
        if (question == null || question.isBlank()) {
            question = stringValue(arguments.get("query"));
        }
        return new KnowledgeQaInput(question, longValue(arguments.get("videoId"), "videoId"));
    }

    private VideoSegmentLocatorInput toVideoSegmentLocatorInput(Map<String, Object> arguments) {
        return new VideoSegmentLocatorInput(
                stringValue(arguments.get("query")),
                longValue(arguments.get("videoId"), "videoId"),
                intValue(arguments.get("topK"), 5, "topK")
        );
    }

    private VideoSummaryInput toVideoSummaryInput(Map<String, Object> arguments) {
        return new VideoSummaryInput(
                longValue(arguments.get("videoId"), "videoId"),
                stringValue(arguments.get("topic")),
                stringValue(arguments.get("summaryType"))
        );
    }

    private QuizInput toQuizInput(Map<String, Object> arguments) {
        return new QuizInput(
                longValue(arguments.get("videoId"), "videoId"),
                stringValue(arguments.get("topic")),
                stringValue(arguments.get("difficulty")),
                intValue(arguments.get("count"), 5, "count")
        );
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Long longValue(Object value, String fieldName) {
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
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a number");
        }
    }

    private Integer intValue(Object value, Integer fallback, String fieldName) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a number");
        }
    }
}
