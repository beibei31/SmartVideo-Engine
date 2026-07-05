package com.example.server.agent.model;

import com.example.server.rag.model.SearchResult;

import java.util.List;

public record KnowledgeQaResult(
        String answer,
        List<SearchResult> contexts
) {
}
