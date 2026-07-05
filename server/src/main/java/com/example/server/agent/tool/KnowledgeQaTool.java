package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.KnowledgeQaInput;
import com.example.server.agent.model.KnowledgeQaResult;
import com.example.server.chat.generation.GenerationService;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeQaTool implements AgentTool<KnowledgeQaInput, KnowledgeQaResult> {

    private final GenerationService generationService;

    public KnowledgeQaTool(GenerationService generationService) {
        this.generationService = generationService;
    }

    @Override
    public String name() {
        return "KnowledgeQaTool";
    }

    @Override
    public KnowledgeQaResult execute(KnowledgeQaInput input, AgentState state) {
        Long videoId = input.videoId() != null ? input.videoId() : state.currentVideoId();
        var result = generationService.generateWithContexts(input.question(), state.sessionId(), videoId);
        return new KnowledgeQaResult(result.answer(), result.contexts());
    }
}
