package com.example.server.agent.tool;

import com.example.server.agent.model.AgentState;
import com.example.server.agent.model.VideoSearchInput;
import com.example.server.agent.model.VideoSegment;
import com.example.server.agent.model.VideoSummaryInput;
import com.example.server.agent.model.VideoSummaryResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VideoSummaryTool implements AgentTool<VideoSummaryInput, VideoSummaryResult> {

    private final VideoSearchTool videoSearchTool;
    private final ChatLanguageModel chatLanguageModel;

    public VideoSummaryTool(VideoSearchTool videoSearchTool, ChatLanguageModel chatLanguageModel) {
        this.videoSearchTool = videoSearchTool;
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public String name() {
        return "VideoSummaryTool";
    }

    @Override
    public VideoSummaryResult execute(VideoSummaryInput input, AgentState state) {
        Long videoId = input.videoId() != null ? input.videoId() : state.currentVideoId();
        String topic = input.topic() != null && !input.topic().isBlank() ? input.topic() : "视频核心内容";
        String summaryType = input.summaryType() != null && !input.summaryType().isBlank()
                ? input.summaryType()
                : "outline";

        var searchResult = videoSearchTool.execute(new VideoSearchInput(topic, videoId, 8), state);
        List<VideoSegment> references = searchResult.segments();
        String summary = chatLanguageModel.chat(buildPrompt(summaryType, topic, references));
        return new VideoSummaryResult(summaryType, summary, references);
    }

    private String buildPrompt(String summaryType, String topic, List<VideoSegment> references) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于视频片段生成 ").append(summaryType).append(" 类型总结。\n");
        sb.append("主题: ").append(topic).append("\n");
        sb.append("要求: 输出中文，结构清晰，保留关键技术点和可引用时间点。\n\n");
        for (int i = 0; i < references.size(); i++) {
            VideoSegment segment = references.get(i);
            sb.append("片段 ").append(i + 1)
                    .append(" [").append(segment.startTime()).append("-").append(segment.endTime()).append("]\n")
                    .append(segment.text()).append("\n\n");
        }
        return sb.toString();
    }
}
