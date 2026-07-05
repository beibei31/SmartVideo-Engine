package com.example.server.agent.model;

import java.util.List;

public record VideoSegmentLocatorResult(
        List<MatchedVideoSegment> matchedSegments
) {
}
