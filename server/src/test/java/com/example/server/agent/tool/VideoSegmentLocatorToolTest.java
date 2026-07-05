package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSearchResult;
import com.example.server.agent.model.VideoSegment;
import com.example.server.agent.model.VideoSegmentLocatorInput;
import com.example.server.agent.model.VideoSegmentLocatorResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoSegmentLocatorToolTest {

    @Test
    void locatesSegmentsByDelegatingToVideoSearchTool() {
        VideoSearchTool videoSearchTool = mock(VideoSearchTool.class);
        VideoSegmentLocatorTool tool = new VideoSegmentLocatorTool(videoSearchTool);
        AgentState state = AgentState.builder().currentVideoId(42L).build();
        VideoSearchResult searchResult = new VideoSearchResult(List.of(
                new VideoSegment(42L, 120L, 180L, "RocketMQ 削峰内容", 0.91, "mq.mp4", "chunk-1")
        ));
        when(videoSearchTool.execute(new VideoSearchInput("RocketMQ 削峰", 42L, 3), state))
                .thenReturn(searchResult);

        VideoSegmentLocatorResult result = tool.execute(
                new VideoSegmentLocatorInput("RocketMQ 削峰", 42L, 3),
                state
        );

        assertEquals(1, result.matchedSegments().size());
        assertEquals(120L, result.matchedSegments().get(0).startTime());
        assertEquals(180L, result.matchedSegments().get(0).endTime());
        assertEquals("RocketMQ 削峰内容", result.matchedSegments().get(0).text());
        assertEquals("匹配问题关键词，检索相关度 0.91", result.matchedSegments().get(0).reason());
        verify(videoSearchTool).execute(new VideoSearchInput("RocketMQ 削峰", 42L, 3), state);
    }
}
