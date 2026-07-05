package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSearchResult;
import com.example.server.rag.model.RetrievalRequest;
import com.example.server.rag.model.SearchResult;
import com.example.server.rag.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoSearchToolTest {

    @Test
    void executesScopedRetrievalAndMapsSegments() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        VideoSearchTool tool = new VideoSearchTool(retrievalService);
        AgentState state = AgentState.builder()
                .sessionId("session-1")
                .currentVideoId(42L)
                .history(List.of("user: 上一个问题"))
                .build();
        SearchResult searchResult = SearchResult.builder()
                .chunkId("video-42-v1-chunk-0")
                .content("Redis 缓存击穿内容")
                .score(0.88)
                .sourceTitle("redis.mp4")
                .metadata(Map.of(
                        "videoId", "42",
                        "startTime", "120",
                        "endTime", "180"
                ))
                .build();

        when(retrievalService.search(org.mockito.ArgumentMatchers.any(RetrievalRequest.class)))
                .thenReturn(List.of(searchResult));

        VideoSearchResult result = tool.execute(new VideoSearchInput("Redis 缓存击穿", 42L, 5), state);

        ArgumentCaptor<RetrievalRequest> captor = ArgumentCaptor.forClass(RetrievalRequest.class);
        verify(retrievalService).search(captor.capture());
        assertEquals("Redis 缓存击穿", captor.getValue().question());
        assertEquals(42L, captor.getValue().videoId());
        assertEquals(true, captor.getValue().hybrid());
        assertEquals(state.history(), captor.getValue().history());
        assertEquals(1, result.segments().size());
        assertEquals(42L, result.segments().get(0).videoId());
        assertEquals(120L, result.segments().get(0).startTime());
        assertEquals(180L, result.segments().get(0).endTime());
        assertEquals("Redis 缓存击穿内容", result.segments().get(0).text());
    }
}
