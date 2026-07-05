package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.MatchedVideoSegment;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSegment;
import com.example.server.agent.model.VideoSegmentLocatorInput;
import com.example.server.agent.model.VideoSegmentLocatorResult;
import org.springframework.stereotype.Component;

@Component
public class VideoSegmentLocatorTool implements AgentTool<VideoSegmentLocatorInput, VideoSegmentLocatorResult> {

    private final VideoSearchTool videoSearchTool;

    public VideoSegmentLocatorTool(VideoSearchTool videoSearchTool) {
        this.videoSearchTool = videoSearchTool;
    }

    @Override
    public String name() {
        return "VideoSegmentLocatorTool";
    }

    @Override
    public VideoSegmentLocatorResult execute(VideoSegmentLocatorInput input, AgentState state) {
        Long videoId = input.videoId() != null ? input.videoId() : state.currentVideoId();
        int topK = input.topK() != null ? input.topK() : 5;
        var searchResult = videoSearchTool.execute(new VideoSearchInput(input.query(), videoId, topK), state);
        return new VideoSegmentLocatorResult(searchResult.segments().stream()
                .map(this::toMatchedSegment)
                .toList());
    }

    private MatchedVideoSegment toMatchedSegment(VideoSegment segment) {
        return new MatchedVideoSegment(
                segment.videoId(),
                segment.startTime(),
                segment.endTime(),
                segment.text(),
                segment.score(),
                "匹配问题关键词，检索相关度 %.2f".formatted(segment.score()),
                segment.sourceTitle(),
                segment.chunkId()
        );
    }
}
