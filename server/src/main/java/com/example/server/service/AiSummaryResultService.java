package com.example.server.service;

import com.example.server.entity.AiSummaryResult;
import com.example.server.mapper.AiSummaryResultMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AiSummaryResultService {

    @Autowired
    private AiSummaryResultMapper aiSummaryResultMapper;

    public void saveResult(Long mediaId, Long userId, String resultJson) {
        if (mediaId == null || resultJson == null || resultJson.isBlank()) {
            return;
        }

        try {
            AiSummaryResult result = new AiSummaryResult();
            result.setMediaId(mediaId);
            result.setUserId(userId);
            result.setResultJson(resultJson);
            result.setCreatedAt(LocalDateTime.now());
            aiSummaryResultMapper.insert(result);
        } catch (Exception e) {
            System.err.println("Failed to save ai_summary_result: " + e.getMessage());
        }
    }
}
