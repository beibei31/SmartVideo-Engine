package com.example.server.agent.controller;

import com.example.server.agent.model.AgentRequest;
import com.example.server.agent.model.AgentResponse;
import com.example.server.agent.model.AgentEvent;
import com.example.server.agent.service.AgentOrchestrator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    @Test
    void chatPassesVideoIdSessionAndQuestionToOrchestrator() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        AgentController controller = new AgentController(orchestrator, Runnable::run);
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
    void chatTreatsInvalidVideoIdAsUnscopedRequest() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        AgentController controller = new AgentController(orchestrator, Runnable::run);
        when(orchestrator.run(org.mockito.ArgumentMatchers.any(AgentRequest.class)))
                .thenReturn(new AgentResponse("session-1", null, "找 Redis", "answer", List.of()));

        AgentResponse response = controller.chat(Map.of(
                "sessionId", "session-1",
                "videoId", "not-a-number",
                "question", "找 Redis"
        ));

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(orchestrator).run(captor.capture());
        assertEquals("session-1", captor.getValue().sessionId());
        assertEquals(null, captor.getValue().videoId());
        assertEquals("answer", response.answer());
    }

    @Test
    void chatRejectsBlankQuestionBeforeCallingOrchestrator() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        AgentController controller = new AgentController(orchestrator, Runnable::run);

        AgentResponse response = controller.chat(Map.of(
                "sessionId", "session-1",
                "videoId", 42,
                "question", "   "
        ));

        assertEquals("session-1", response.sessionId());
        assertEquals(42L, response.videoId());
        assertEquals("问题不能为空", response.answer());
        verify(orchestrator, never()).run(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void streamChatUsesConfiguredExecutorAndPassesRequestToStreamingOrchestrator() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        Executor sameThreadExecutor = Runnable::run;
        AgentController controller = new AgentController(orchestrator, sameThreadExecutor);

        SseEmitter emitter = controller.streamChat(Map.of(
                "sessionId", "session-1",
                "videoId", 42,
                "question", "找 Redis"
        ));

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(orchestrator).runStreaming(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertEquals("session-1", captor.getValue().sessionId());
        assertEquals(42L, captor.getValue().videoId());
        assertEquals("找 Redis", captor.getValue().question());
        assertEquals(SseEmitter.class, emitter.getClass());
    }

    @Test
    void streamChatSendsErrorAndDoneThenCompletesNormallyWhenOrchestratorFails() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        CapturingSseEmitter capturingEmitter = new CapturingSseEmitter();
        Supplier<SseEmitter> emitterFactory = () -> capturingEmitter;
        CapturingAgentController controller = new CapturingAgentController(orchestrator, Runnable::run, emitterFactory);
        when(orchestrator.runStreaming(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("planner unavailable"));

        controller.streamChat(Map.of(
                "sessionId", "session-1",
                "question", "Redis"
        ));

        assertEquals(List.of("error", "done"), controller.eventNames());
        assertEquals("planner unavailable", controller.events.get(0).data());
        assertEquals(true, capturingEmitter.completed);
        assertEquals(false, capturingEmitter.completedWithError);
    }

    @Test
    void streamChatRejectsBlankQuestionWithErrorAndDoneBeforeCallingOrchestrator() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        CapturingSseEmitter capturingEmitter = new CapturingSseEmitter();
        CapturingAgentController controller = new CapturingAgentController(
                orchestrator,
                Runnable::run,
                () -> capturingEmitter
        );

        controller.streamChat(Map.of(
                "sessionId", "session-1",
                "question", " "
        ));

        assertEquals(List.of("error", "done"), controller.eventNames());
        assertEquals("问题不能为空", controller.events.get(0).data());
        assertEquals(true, capturingEmitter.completed);
        verify(orchestrator, never()).runStreaming(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static class CapturingAgentController extends AgentController {
        private final java.util.List<AgentEvent> events = new java.util.ArrayList<>();

        CapturingAgentController(AgentOrchestrator orchestrator,
                                 Executor executor,
                                 Supplier<SseEmitter> emitterFactory) {
            super(orchestrator, executor, emitterFactory);
        }

        @Override
        protected void sendEvent(SseEmitter emitter, AgentEvent event) {
            events.add(event);
        }

        private List<String> eventNames() {
            return events.stream().map(AgentEvent::event).toList();
        }
    }

    private static class CapturingSseEmitter extends SseEmitter {
        private boolean completed;
        private boolean completedWithError;

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable ex) {
            completedWithError = true;
        }
    }
}
