package com.example.server.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AliyunAsrUtils {

    @Value("${ai.aliyun.api-key}")
    private String apiKey;

    @Value("${ai.aliyun.asr-url:https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription}")
    private String asrUrl;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public String audioToText(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.startsWith("http")) {
            return "识别失败: 百炼ASR需要公网可访问的音视频URL";
        }
        try {
            String taskId = submitTask(fileUrl);
            if (taskId == null || taskId.isBlank()) {
                return "识别失败: 任务提交失败，未返回task_id";
            }
            return pollTaskResult(taskId);
        } catch (Exception e) {
            return "识别失败: " + e.getMessage();
        }
    }

    private String submitTask(String fileUrl) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", "paraformer-v2");
        JSONObject input = new JSONObject();
        input.put("file_urls", new String[]{fileUrl});
        body.put("input", input);

        Request request = new Request.Builder()
                .url(asrUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-DashScope-Async", "enable")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code() + ": " + responseBody);
            }
            JSONObject json = JSON.parseObject(responseBody);
            JSONObject output = json.getJSONObject("output");
            return output == null ? null : output.getString("task_id");
        }
    }

    private String pollTaskResult(String taskId) throws Exception {
        String taskUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
        for (int i = 0; i < 60; i++) {
            Request request = new Request.Builder()
                    .url(taskUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP " + response.code() + ": " + responseBody);
                }
                JSONObject json = JSON.parseObject(responseBody);
                JSONObject output = json.getJSONObject("output");
                if (output == null) {
                    throw new RuntimeException("Response missing output: " + responseBody);
                }
                String taskStatus = output.getString("task_status");
                if ("SUCCEEDED".equalsIgnoreCase(taskStatus)) {
                    return extractTextFromTaskResult(output);
                }
                if ("FAILED".equalsIgnoreCase(taskStatus) || "CANCELED".equalsIgnoreCase(taskStatus)) {
                    return "识别失败: " + responseBody;
                }
            }
            Thread.sleep(2000);
        }
        return "识别失败: 任务轮询超时";
    }

    private String extractTextFromTaskResult(JSONObject output) throws Exception {
        // Try direct text field first
        String text = output.getString("text");
        if (text != null && !text.isBlank()) {
            return text;
        }

        // Try results array
        JSONArray results = output.getJSONArray("results");
        if (results != null && !results.isEmpty()) {
            JSONObject firstResult = results.getJSONObject(0);
            String subtaskStatus = firstResult.getString("subtask_status");
            if (!"SUCCEEDED".equalsIgnoreCase(subtaskStatus)) {
                return "识别失败: subtask " + subtaskStatus + " - " + firstResult.getString("message");
            }

            // Download and parse transcription_url
            String transcriptionUrl = firstResult.getString("transcription_url");
            if (transcriptionUrl != null && !transcriptionUrl.isBlank()) {
                return downloadTranscription(transcriptionUrl);
            }

            // Fallback: return raw results
            return results.toString();
        }

        return "识别失败: 未解析到文本结果";
    }

    private String downloadTranscription(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to download transcription: HTTP " + response.code());
            }
            String body = response.body() != null ? response.body().string() : "";
            JSONObject json = JSON.parseObject(body);
            JSONArray transcripts = json.getJSONArray("transcripts");
            if (transcripts != null && !transcripts.isEmpty()) {
                return transcripts.getJSONObject(0).getString("text");
            }
            return "识别失败: transcription 结果为空";
        }
    }
}
