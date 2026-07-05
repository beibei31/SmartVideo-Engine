package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.QuizInput;
import com.example.server.agent.model.QuizResult;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuizTool implements AgentTool<QuizInput, QuizResult> {

    private final VideoSearchTool videoSearchTool;
    private final ChatLanguageModel chatLanguageModel;

    public QuizTool(VideoSearchTool videoSearchTool, ChatLanguageModel chatLanguageModel) {
        this.videoSearchTool = videoSearchTool;
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public String name() {
        return "QuizTool";
    }

    @Override
    public QuizResult execute(QuizInput input, AgentState state) {
        Long videoId = input.videoId() != null ? input.videoId() : state.currentVideoId();
        String topic = input.topic() != null && !input.topic().isBlank() ? input.topic() : "视频知识点";
        String difficulty = input.difficulty() != null && !input.difficulty().isBlank() ? input.difficulty() : "medium";
        int count = input.count() != null && input.count() > 0 ? input.count() : 5;

        List<VideoSegment> references = videoSearchTool
                .execute(new VideoSearchInput(topic, videoId, 8), state)
                .segments();
        String quiz = chatLanguageModel.chat(buildPrompt(topic, difficulty, count, references));
        return new QuizResult(topic, difficulty, count, quiz, references);
    }

    private String buildPrompt(String topic, String difficulty, int count, List<VideoSegment> references) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于视频片段生成自测题。\n");
        sb.append("主题: ").append(topic).append("\n");
        sb.append("难度: ").append(difficulty).append("\n");
        sb.append("题目数量: ").append(count).append("\n");
        sb.append("要求: 使用中文，包含答案和简短解析，题目应覆盖视频中的关键知识点。\n\n");
        for (int i = 0; i < references.size(); i++) {
            VideoSegment segment = references.get(i);
            sb.append("片段 ").append(i + 1)
                    .append(" [").append(segment.startTime()).append("-").append(segment.endTime()).append("]\n")
                    .append(segment.text()).append("\n\n");
        }
        return sb.toString();
    }
}
