package com.example.server.agent.model;

public record VideoSegment(
        Long videoId,
        Long startTime,
        Long endTime,
        String text,
        double score,
        String sourceTitle,
        String chunkId
) {
}
