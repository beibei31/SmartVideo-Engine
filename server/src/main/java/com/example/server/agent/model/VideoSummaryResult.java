package com.example.server.agent.model;

import java.util.List;

public record VideoSummaryResult(
        String summaryType,
        String summary,
        List<VideoSegment> references
) {
}
