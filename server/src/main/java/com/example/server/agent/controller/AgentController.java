package com.example.server.agent.controller;

import com.example.server.agent.model.AgentEvent;
import com.example.server.agent.model.AgentRequest;
import com.example.server.agent.model.AgentResponse;
import com.example.server.agent.service.AgentOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;

    public AgentController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody Map<String, Object> request) {
        return agentOrchestrator.run(toAgentRequest(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, Object> request) {
        AgentRequest agentRequest = toAgentRequest(request);
        SseEmitter emitter = new SseEmitter(300_000L);
        CompletableFuture.runAsync(() -> {
            try {
                agentOrchestrator.runStreaming(agentRequest, event -> sendEvent(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                sendEvent(emitter, new AgentEvent("error", e.getMessage()));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private AgentRequest toAgentRequest(Map<String, Object> request) {
        String sessionId = stringValue(request.get("sessionId"));
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        return new AgentRequest(
                sessionId,
                longValue(request.get("videoId")),
                stringValue(request.get("question")),
                stringValue(request.get("mode"))
        );
    }

    private void sendEvent(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.event()).data(event.data()));
        } catch (IOException ignored) {
        }
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
        if (text.isBlank()) {
            return null;
        }
        return Long.parseLong(text);
    }
}
