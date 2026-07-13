package com.example.server.chat.memory;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMemoryServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);

    @Test
    void doesNotSummarizeImmediatelyAfterWindowIsSlightlyExceeded() {
        ChatMemoryService service = new ChatMemoryService(
                redisTemplate,
                chatLanguageModel,
                12,
                16,
                7,
                true
        );
        JSONArray existing = messages(12);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:memory:s1")).thenReturn(existing.toJSONString());

        service.save("s1", "new question", "new answer");

        verify(chatLanguageModel, never()).chat(org.mockito.ArgumentMatchers.anyString());
        verify(valueOperations).set(
                eq("chat:memory:s1"),
                org.mockito.ArgumentMatchers.argThat(json -> JSONSize(json) == 14),
                eq(7L),
                eq(TimeUnit.DAYS)
        );
    }

    @Test
    void summarizesOnlyWhenTriggerThresholdIsExceededAndKeepsWindowBounded() {
        ChatMemoryService service = new ChatMemoryService(
                redisTemplate,
                chatLanguageModel,
                12,
                16,
                7,
                true
        );
        JSONArray existing = messages(16);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:memory:s1")).thenReturn(existing.toJSONString());
        when(chatLanguageModel.chat(org.mockito.ArgumentMatchers.anyString())).thenReturn("summary");

        service.save("s1", "new question", "new answer");

        verify(chatLanguageModel).chat(org.mockito.ArgumentMatchers.anyString());
        verify(valueOperations).set(
                eq("chat:memory:s1"),
                org.mockito.ArgumentMatchers.argThat(json -> JSONSize(json) == 12),
                eq(7L),
                eq(TimeUnit.DAYS)
        );
    }

    @Test
    void usesAtLeastOneDayTtlWhenConfiguredTtlIsTooSmall() {
        ChatMemoryService service = new ChatMemoryService(
                redisTemplate,
                chatLanguageModel,
                12,
                16,
                0,
                true
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("chat:memory:s1")).thenReturn(null);

        service.save("s1", "question", "answer");

        verify(valueOperations).set(
                eq("chat:memory:s1"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(1L),
                eq(TimeUnit.DAYS)
        );
    }

    private JSONArray messages(int count) {
        JSONArray messages = new JSONArray();
        for (int i = 0; i < count; i++) {
            JSONObject entry = new JSONObject();
            entry.put("role", i % 2 == 0 ? "user" : "assistant");
            entry.put("content", "message-" + i);
            entry.put("timestamp", i);
            messages.add(entry);
        }
        return messages;
    }

    private int JSONSize(String json) {
        return JSONArray.parseArray(json).size();
    }
}
