package com.example.server.chat.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ChatMemoryService {

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ChatLanguageModel chatLanguageModel;
    private final int maxMessages;
    private final int summaryTriggerMessages;
    private final long ttlDays;
    private final boolean summaryEnabled;

    public ChatMemoryService(StringRedisTemplate redisTemplate,
                             ChatLanguageModel chatLanguageModel,
                             @Value("${chat.memory.max-messages:12}") int maxMessages,
                             @Value("${chat.memory.summary-trigger-messages:16}") int summaryTriggerMessages,
                             @Value("${chat.memory.ttl-days:7}") long ttlDays,
                             @Value("${chat.memory.summary-enabled:true}") boolean summaryEnabled) {
        this.redisTemplate = redisTemplate;
        this.chatLanguageModel = chatLanguageModel;
        this.maxMessages = Math.max(4, maxMessages);
        this.summaryTriggerMessages = Math.max(this.maxMessages, summaryTriggerMessages);
        this.ttlDays = Math.max(1L, ttlDays);
        this.summaryEnabled = summaryEnabled;
    }

    public void save(String sessionId, String userMsg, String assistantMsg) {
        JSONArray messages = loadMessages(sessionId);
        messages.add(message("user", userMsg));
        messages.add(message("assistant", assistantMsg));

        if (messages.size() > summaryTriggerMessages) {
            compressWithSummary(messages);
        }

        redisTemplate.opsForValue().set(
                KEY_PREFIX + sessionId,
                messages.toJSONString(),
                ttlDays,
                TimeUnit.DAYS
        );

        log.debug("Chat memory saved: session={}, messages={}", sessionId, messages.size());
    }

    public List<String> loadHistory(String sessionId) {
        JSONArray messages = loadMessages(sessionId);
        List<String> history = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            JSONObject msg = messages.getJSONObject(i);
            String role = msg.getString("role");
            String content = msg.getString("content");

            switch (role) {
                case "system" -> history.add("[历史摘要] " + content);
                case "user" -> history.add("用户: " + content);
                case "assistant" -> history.add("助手: " + content);
                default -> history.add(role + ": " + content);
            }
        }

        return history;
    }

    public void clear(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
        log.debug("Chat memory cleared: session={}", sessionId);
    }

    public int size(String sessionId) {
        return loadMessages(sessionId).size();
    }

    private void compressWithSummary(JSONArray messages) {
        int targetSizeBeforeSummary = maxMessages - 1;
        int overflow = messages.size() - targetSizeBeforeSummary;
        if (overflow <= 0) {
            return;
        }

        List<String> oldLines = new ArrayList<>();
        for (int i = 0; i < overflow; i++) {
            JSONObject msg = messages.getJSONObject(i);
            oldLines.add(formatForSummary(msg));
        }

        String summary = null;
        if (!oldLines.isEmpty() && summaryEnabled) {
            try {
                summary = generateSummary(oldLines);
            } catch (Exception e) {
                log.warn("Chat memory summary failed; old messages will be dropped: {}", e.getMessage());
            }
        }

        for (int i = 0; i < overflow; i++) {
            messages.remove(0);
        }

        if (summary != null && !summary.isBlank()) {
            messages.add(0, message("system", summary));
        }

        log.debug("Chat memory compressed: overflow={}, current={}", overflow, messages.size());
    }

    private String generateSummary(List<String> oldLines) {
        String prompt = """
                请用一到两句话总结以下对话的关键信息，保留人名、实体、主题、结论和用户偏好：

                %s

                摘要：
                """.formatted(String.join("\n", oldLines));

        String result = chatLanguageModel.chat(prompt).trim();
        result = result.replaceAll("^摘要[:：]?\\s*", "");
        return result;
    }

    private JSONArray loadMessages(String sessionId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null || json.isBlank()) {
            return new JSONArray();
        }

        try {
            return JSON.parseArray(json);
        } catch (Exception e) {
            log.warn("Failed to parse chat memory JSON; returning empty history: {}", e.getMessage());
            return new JSONArray();
        }
    }

    private JSONObject message(String role, String content) {
        JSONObject entry = new JSONObject();
        entry.put("role", role);
        entry.put("content", content);
        entry.put("timestamp", System.currentTimeMillis());
        return entry;
    }

    private String formatForSummary(JSONObject msg) {
        String role = msg.getString("role");
        String content = msg.getString("content");
        if ("system".equals(role)) {
            return "[早期摘要] " + content;
        }
        if ("user".equals(role)) {
            return "用户: " + content;
        }
        if ("assistant".equals(role)) {
            return "助手: " + content;
        }
        return role + ": " + content;
    }
}
