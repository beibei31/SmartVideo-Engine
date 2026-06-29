package com.example.server.rag.retrieval;

import com.example.server.rag.mapper.RagChunkDocumentMapper;
import com.example.server.rag.model.SearchResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RagChunkValidator {

    private final RagChunkDocumentMapper chunkDocumentMapper;

    public RagChunkValidator(RagChunkDocumentMapper chunkDocumentMapper) {
        this.chunkDocumentMapper = chunkDocumentMapper;
    }

    public List<SearchResult> keepActive(List<SearchResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<String> chunkIds = candidates.stream()
                .map(SearchResult::getChunkId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            return List.of();
        }

        Set<String> activeChunkIds = new LinkedHashSet<>(chunkDocumentMapper.selectActiveChunkIds(chunkIds));
        return candidates.stream()
                .filter(candidate -> activeChunkIds.contains(candidate.getChunkId()))
                .toList();
    }
}
