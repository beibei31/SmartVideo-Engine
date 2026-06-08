package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.server.context.TokenUsageContext;
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
                你是一个专业的视频内容分析专家。
                用户会提供一段带有精确时间戳的视频转写文本，格式为：[开始秒数 - 结束秒数] 句子内容。
                请根据上下文语义，将内容划分为几个核心章节，并输出严格的 JSON 数组格式。

                【绝对指令 - 必须严格遵守】
                1. 时间戳提取：每个章节的 startTime 必须严格等于该章节第一句话的 [开始秒数]。绝不允许自己计算、估算或编造时间！
                2. 提取精度：startTime 请保留为数字类型，如果是 12.5s，则输出 12.5。
                3. 摘要浓缩：每个章节的 summary 请控制在 50 字以内，直击要点。

                【输出格式标准】
                [
                  {
                    "startTime": 0.0,
                    "topic": "前言介绍",
                    "summary": "介绍了本次教程的核心目的和所需环境。"
                  },
                  {
                    "startTime": 45.2,
                    "topic": "核心代码编写",
                    "summary": "演示了如何配置拦截器并解析时间戳。"
                  }
                ]""";

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
