package com.example.server.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.ToolCall;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Set;
import java.util.Map;

@Slf4j
@Service
public class PlannerService {

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "VideoSearchTool",
            "VideoSegmentLocatorTool",
            "VideoSummaryTool",
            "QuizTool",
            "KnowledgeQaTool",
            "FinalAnswer"
    );

    private final ChatLanguageModel chatLanguageModel;

    public PlannerService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public ToolCall plan(AgentState state, String question) {
        String response = chatLanguageModel.chat(buildPrompt(state, question));
        try {
            JSONObject json = JSON.parseObject(extractJson(response));
            String thought = json.getString("thought");
            String action = json.getString("action");
            JSONObject argumentsJson = json.getJSONObject("arguments");
            Map<String, Object> arguments = argumentsJson != null
                    ? new HashMap<>(argumentsJson)
                    : new HashMap<>();
            if (action == null || action.isBlank() || !ALLOWED_ACTIONS.contains(action)) {
                return fallback(state, question);
            }
            fillDefaultArguments(action, arguments, state, question);
            return new ToolCall(thought, action, arguments);
        } catch (Exception e) {
            log.warn("Planner returned invalid JSON, using fallback action: {}", response);
            return fallback(state, question);
        }
    }

    private ToolCall fallback(AgentState state, String question) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("question", question);
        arguments.put("videoId", state.currentVideoId());
        return new ToolCall("Planner JSON invalid; fallback to scoped RAG QA", "KnowledgeQaTool", arguments);
    }

    private String extractJson(String response) {
        if (response == null) {
            return "";
        }
        String text = response.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private void fillDefaultArguments(String action,
                                      Map<String, Object> arguments,
                                      AgentState state,
                                      String question) {
        switch (action) {
            case "VideoSearchTool", "VideoSegmentLocatorTool" -> {
                arguments.putIfAbsent("query", question);
                arguments.putIfAbsent("videoId", state.currentVideoId());
                arguments.putIfAbsent("topK", 5);
            }
            case "VideoSummaryTool" -> {
                arguments.putIfAbsent("topic", question);
                arguments.putIfAbsent("videoId", state.currentVideoId());
                arguments.putIfAbsent("summaryType", "outline");
            }
            case "QuizTool" -> {
                arguments.putIfAbsent("topic", question);
                arguments.putIfAbsent("videoId", state.currentVideoId());
                arguments.putIfAbsent("difficulty", "medium");
                arguments.putIfAbsent("count", 5);
            }
            case "KnowledgeQaTool" -> {
                arguments.putIfAbsent("question", question);
                arguments.putIfAbsent("videoId", state.currentVideoId());
            }
            default -> {
            }
        }
    }

    private String buildPrompt(AgentState state, String question) {
        return """
                You are a controlled planner for a video learning agent.
                Return only valid JSON with this schema:
                {
                  "thought": "short reason",
                  "action": "VideoSearchTool | VideoSegmentLocatorTool | VideoSummaryTool | QuizTool | KnowledgeQaTool | FinalAnswer",
                  "arguments": {}
                }
                Rules:
                - Use VideoSegmentLocatorTool when the user asks where a topic appears or wants timestamps.
                - Use VideoSearchTool when the user asks to retrieve transcript segments for a topic.
                - Use VideoSummaryTool when the user asks for timeline, outline, interview, or mindmap summaries.
                - Use QuizTool when the user asks for self-test questions, quiz, exam, or practice questions.
                - Use KnowledgeQaTool when the user asks a knowledge question.
                - Use FinalAnswer only when observations already contain enough information.
                Current videoId: %s
                User question: %s
                """.formatted(state.currentVideoId(), question);
    }
}
