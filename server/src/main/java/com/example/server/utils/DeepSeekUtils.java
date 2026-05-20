package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.server.service.TokenUsageContext;
import com.example.server.service.TokenUsageService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class DeepSeekUtils {

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Autowired
    private TokenUsageService tokenUsageService;

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public String analyzeContent(String content) {
        String url = baseUrl + "/chat/completions";
        String systemPrompt = """
                你是一个视频内容提炼专家。请从用户提供的视频转写文本中提取 5 到 8 个核心关键点，生成可点击的视频时间轴导览。

                必须严格遵守以下规则：
                1. 只能输出 JSON 数组，不要输出 Markdown、解释、代码块、前后缀或任何额外废话。
                2. 数组元素格式必须是：{"startTime": 120, "topic": "分布式锁原理", "summary": "这一段讲了..."}。
                3. startTime 必须是整数秒数。如果原文没有明确时间戳，请根据内容顺序估算，从 0 开始均匀递增。
                4. topic 使用 6 到 14 个中文字符，summary 使用 20 到 60 个中文字符。
                5. 输出必须能被 JSON.parse 直接解析。

                示例：
                [{"startTime":0,"topic":"开场背景","summary":"介绍本期视频的问题背景和主要讨论方向。"},{"startTime":120,"topic":"分布式锁原理","summary":"解释分布式锁如何协调多实例并发访问共享资源。"}]
                """;

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("model", "deepseek-chat");
        jsonBody.put("stream", false);

        JSONArray messages = new JSONArray();
        messages.add(JSONObject.of("role", "system", "content", systemPrompt));
        messages.add(JSONObject.of("role", "user", "content", content));
        jsonBody.put("messages", messages);

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() == null ? "" : response.body().string();
                return "AI request failed: " + response.code() + " - " + errorBody;
            }

            if (response.body() == null) {
                return "AI request failed: empty response body";
            }

            String resultJson = response.body().string();
            JSONObject jsonObject = JSON.parseObject(resultJson);
            recordTokenUsage(jsonObject);

            return jsonObject.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (IOException e) {
            e.printStackTrace();
            return "AI network error: " + e.getMessage();
        }
    }

    private void recordTokenUsage(JSONObject jsonObject) {
        JSONObject usage = jsonObject.getJSONObject("usage");
        if (usage == null) {
            return;
        }

        int totalTokens = usage.getIntValue("total_tokens");
        tokenUsageService.recordUsage(TokenUsageContext.getUserId(), totalTokens);
    }
}
