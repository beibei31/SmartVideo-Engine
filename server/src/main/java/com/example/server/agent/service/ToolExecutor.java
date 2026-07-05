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
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ToolExecutor {

    private final VideoSearchTool videoSearchTool;
    private final KnowledgeQaTool knowledgeQaTool;
    private final VideoSegmentLocatorTool videoSegmentLocatorTool;
    private final VideoSummaryTool videoSummaryTool;
    private final QuizTool quizTool;

    public ToolExecutor(VideoSearchTool videoSearchTool,
                        KnowledgeQaTool knowledgeQaTool,
                        VideoSegmentLocatorTool videoSegmentLocatorTool,
                        VideoSummaryTool videoSummaryTool,
                        QuizTool quizTool) {
        this.videoSearchTool = videoSearchTool;
        this.knowledgeQaTool = knowledgeQaTool;
        this.videoSegmentLocatorTool = videoSegmentLocatorTool;
        this.videoSummaryTool = videoSummaryTool;
        this.quizTool = quizTool;
    }

    public ToolResult execute(ToolCall call, AgentState state) {
        try {
            return switch (call.action()) {
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
        } catch (Exception e) {
            return ToolResult.failure(call.action(), e.getMessage());
        }
    }

    private VideoSearchInput toVideoSearchInput(Map<String, Object> arguments) {
        return new VideoSearchInput(
                stringValue(arguments.get("query")),
                longValue(arguments.get("videoId")),
                intValue(arguments.get("topK"), 5)
        );
    }

    private KnowledgeQaInput toKnowledgeQaInput(Map<String, Object> arguments) {
        String question = stringValue(arguments.get("question"));
        if (question == null || question.isBlank()) {
            question = stringValue(arguments.get("query"));
        }
        return new KnowledgeQaInput(question, longValue(arguments.get("videoId")));
    }

    private VideoSegmentLocatorInput toVideoSegmentLocatorInput(Map<String, Object> arguments) {
        return new VideoSegmentLocatorInput(
                stringValue(arguments.get("query")),
                longValue(arguments.get("videoId")),
                intValue(arguments.get("topK"), 5)
        );
    }

    private VideoSummaryInput toVideoSummaryInput(Map<String, Object> arguments) {
        return new VideoSummaryInput(
                longValue(arguments.get("videoId")),
                stringValue(arguments.get("topic")),
                stringValue(arguments.get("summaryType"))
        );
    }

    private QuizInput toQuizInput(Map<String, Object> arguments) {
        return new QuizInput(
                longValue(arguments.get("videoId")),
                stringValue(arguments.get("topic")),
                stringValue(arguments.get("difficulty")),
                intValue(arguments.get("count"), 5)
        );
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : Long.parseLong(text);
    }

    private Integer intValue(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : Integer.parseInt(text);
    }
}
