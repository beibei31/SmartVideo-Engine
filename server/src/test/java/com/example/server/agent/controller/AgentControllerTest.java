package com.example.server.agent.controller;

import com.example.server.agent.model.AgentRequest;
import com.example.server.agent.model.AgentResponse;
import com.example.server.agent.service.AgentOrchestrator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    @Test
    void chatPassesVideoIdSessionAndQuestionToOrchestrator() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        AgentController controller = new AgentController(orchestrator);
        when(orchestrator.run(org.mockito.ArgumentMatchers.any(AgentRequest.class)))
                .thenReturn(new AgentResponse("session-1", 42L, "找 Redis", "answer", List.of()));

        AgentResponse response = controller.chat(Map.of(
                "sessionId", "session-1",
                "videoId", 42,
                "question", "找 Redis"
        ));

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(orchestrator).run(captor.capture());
        assertEquals("session-1", captor.getValue().sessionId());
        assertEquals(42L, captor.getValue().videoId());
        assertEquals("找 Redis", captor.getValue().question());
        assertEquals("answer", response.answer());
    }

    @Test
    void streamChatPassesRequestToStreamingOrchestrator() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        AgentController controller = new AgentController(orchestrator);

        SseEmitter emitter = controller.streamChat(Map.of(
                "sessionId", "session-1",
                "videoId", 42,
                "question", "找 Redis"
        ));

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(orchestrator, timeout(1000)).runStreaming(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertEquals("session-1", captor.getValue().sessionId());
        assertEquals(42L, captor.getValue().videoId());
        assertEquals("找 Redis", captor.getValue().question());
        assertEquals(SseEmitter.class, emitter.getClass());
    }
}
