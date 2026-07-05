package com.example.server.agent.model;

public record MatchedVideoSegment(
        Long videoId,
        Long startTime,
        Long endTime,
        String text,
        double score,
        String reason,
        String sourceTitle,
        String chunkId
) {
}
