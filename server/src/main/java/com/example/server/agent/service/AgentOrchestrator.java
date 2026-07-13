package com.example.server.agent.service;

import com.example.server.agent.model.AgentRequest;
import com.example.server.agent.model.AgentResponse;
import com.example.server.agent.model.AgentEvent;
import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.AgentStep;
import com.example.server.agent.model.KnowledgeQaResult;
import com.example.server.agent.model.MatchedVideoSegment;
import com.example.server.agent.model.QuizResult;
import com.example.server.agent.model.ToolCall;
import com.example.server.agent.model.ToolResult;
import com.example.server.agent.model.VideoSearchResult;
import com.example.server.agent.model.VideoSegmentLocatorResult;
import com.example.server.agent.model.VideoSummaryResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class AgentOrchestrator {

    private final PlannerService plannerService;
    private final ToolExecutor toolExecutor;
    private final int maxSteps;

    public AgentOrchestrator(PlannerService plannerService,
                             ToolExecutor toolExecutor,
                             @Value("${agent.max-steps:5}") int maxSteps) {
        this.plannerService = plannerService;
        this.toolExecutor = toolExecutor;
        this.maxSteps = maxSteps;
    }

    public AgentResponse run(AgentRequest request) {
        List<AgentStep> steps = new ArrayList<>();
        AgentState state = AgentState.builder()
                .sessionId(request.sessionId())
                .currentVideoId(request.videoId())
                .steps(steps)
                .build();

        for (int i = 0; i < maxSteps; i++) {
            ToolCall call = plannerService.plan(state, request.question());
            if ("FinalAnswer".equals(call.action())) {
                return response(request, answerFromFinalCall(call), steps);
            }

            ToolResult result = toolExecutor.execute(call, state);
            steps.add(new AgentStep(call, result));
            String directAnswer = answerFromToolResult(result);
            if (directAnswer != null) {
                return response(request, directAnswer, steps);
            }
            state = AgentState.builder()
                    .sessionId(request.sessionId())
                    .currentVideoId(request.videoId())
                    .history(state.history())
                    .steps(steps)
                    .build();
        }

        return response(request, "已达到最大执行步数，系统基于当前工具结果停止继续规划。", steps);
    }

    public AgentResponse runStreaming(AgentRequest request, Consumer<AgentEvent> eventSink) {
        List<AgentStep> steps = new ArrayList<>();
        AgentState state = AgentState.builder()
                .sessionId(request.sessionId())
                .currentVideoId(request.videoId())
                .steps(steps)
                .build();

        eventSink.accept(new AgentEvent("agent_status", "正在分析用户意图"));
        for (int i = 0; i < maxSteps; i++) {
            ToolCall call = plannerService.plan(state, request.question());
            if ("FinalAnswer".equals(call.action())) {
                String answer = answerFromFinalCall(call);
                eventSink.accept(new AgentEvent("final_answer", answer));
                eventSink.accept(new AgentEvent("done", ""));
                return response(request, answer, steps);
            }

            eventSink.accept(new AgentEvent("tool_call", call));
            ToolResult result = toolExecutor.execute(call, state);
            steps.add(new AgentStep(call, result));
            eventSink.accept(new AgentEvent("tool_result", result));
            emitContexts(result, eventSink);

            String directAnswer = answerFromToolResult(result);
            if (directAnswer != null) {
                eventSink.accept(new AgentEvent("final_answer", directAnswer));
                eventSink.accept(new AgentEvent("done", ""));
                return response(request, directAnswer, steps);
            }

            state = AgentState.builder()
                    .sessionId(request.sessionId())
                    .currentVideoId(request.videoId())
                    .history(state.history())
                    .steps(steps)
                    .build();
        }

        String answer = "已达到最大执行步数，系统基于当前工具结果停止继续规划。";
        eventSink.accept(new AgentEvent("final_answer", answer));
        eventSink.accept(new AgentEvent("done", ""));
        return response(request, answer, steps);
    }

    private AgentResponse response(AgentRequest request, String answer, List<AgentStep> steps) {
        return new AgentResponse(
                request.sessionId(),
                request.videoId(),
                request.question(),
                answer,
                List.copyOf(steps)
        );
    }

    private String answerFromFinalCall(ToolCall call) {
        Object answer = call.arguments() != null ? call.arguments().get("answer") : null;
        if (answer == null || String.valueOf(answer).isBlank()) {
            return "已完成 Agent 规划。";
        }
        return String.valueOf(answer);
    }

    private String answerFromToolResult(ToolResult result) {
        if (!result.success() || result.data() == null) {
            return null;
        }
        if (result.data() instanceof KnowledgeQaResult qaResult) {
            return qaResult.answer();
        }
        if (result.data() instanceof VideoSummaryResult summaryResult) {
            return summaryResult.summary();
        }
        if (result.data() instanceof QuizResult quizResult) {
            return quizResult.quiz();
        }
        if (result.data() instanceof VideoSegmentLocatorResult locatorResult) {
            return formatLocatedSegments(locatorResult);
        }
        return null;
    }

    private void emitContexts(ToolResult result, Consumer<AgentEvent> eventSink) {
        if (!result.success() || result.data() == null) {
            return;
        }
        if (result.data() instanceof KnowledgeQaResult qaResult && !qaResult.contexts().isEmpty()) {
            eventSink.accept(new AgentEvent("contexts", qaResult.contexts()));
            return;
        }
        if (result.data() instanceof VideoSearchResult searchResult && !searchResult.segments().isEmpty()) {
            eventSink.accept(new AgentEvent("contexts", searchResult.segments()));
            return;
        }
        if (result.data() instanceof VideoSegmentLocatorResult locatorResult && !locatorResult.matchedSegments().isEmpty()) {
            eventSink.accept(new AgentEvent("contexts", locatorResult.matchedSegments()));
        }
    }

    private String formatLocatedSegments(VideoSegmentLocatorResult locatorResult) {
        if (locatorResult.matchedSegments().isEmpty()) {
            return "没有找到相关视频片段。";
        }
        StringBuilder sb = new StringBuilder("找到以下相关视频片段：\n");
        for (MatchedVideoSegment segment : locatorResult.matchedSegments()) {
            sb.append("- [")
                    .append(segment.startTime())
                    .append(" - ")
                    .append(segment.endTime())
                    .append("] ")
                    .append(segment.reason())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
