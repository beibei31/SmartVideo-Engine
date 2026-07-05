package com.example.server.agent.model;

import java.util.List;

public record VideoSearchResult(
        List<VideoSegment> segments
) {
}
