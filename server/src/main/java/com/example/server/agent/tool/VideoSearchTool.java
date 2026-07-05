package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSearchResult;
import com.example.server.agent.model.VideoSegment;
import com.example.server.rag.model.RetrievalRequest;
import com.example.server.rag.model.SearchResult;
import com.example.server.rag.retrieval.RetrievalService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class VideoSearchTool implements AgentTool<VideoSearchInput, VideoSearchResult> {

    private final RetrievalService retrievalService;

    public VideoSearchTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return "VideoSearchTool";
    }

    @Override
    public VideoSearchResult execute(VideoSearchInput input, AgentState state) {
        Long videoId = input.videoId() != null ? input.videoId() : state.currentVideoId();
        List<SearchResult> results = retrievalService.search(RetrievalRequest.builder()
                .question(input.query())
                .videoId(videoId)
                .hybrid(true)
                .history(state.history())
                .topK(input.topK())
                .build());

        return new VideoSearchResult(results.stream()
                .map(result -> toSegment(result, videoId))
                .toList());
    }

    private VideoSegment toSegment(SearchResult result, Long fallbackVideoId) {
        Map<String, Object> metadata = result.getMetadata();
        return new VideoSegment(
                longValue(metadata.get("videoId"), fallbackVideoId),
                longValue(metadata.get("startTime"), null),
                longValue(metadata.get("endTime"), null),
                result.getContent(),
                result.getScore(),
                result.getSourceTitle(),
                result.getChunkId()
        );
    }

    private Long longValue(Object value, Long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return fallback;
        }
        return Long.parseLong(text);
    }
}
