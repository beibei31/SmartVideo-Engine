package com.example.server.consumer;

import com.example.server.dto.AnalysisTaskMsg;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.service.AiService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI 分析任务消费者
 *
 * 指数退避重试策略：
 * 1. 消费者不 catch 异常 → 异常抛给 RocketMQ 触发重试
 * 2. RocketMQ 默认 18 级延迟：1s/5s/10s/30s/1m/2m/.../2h
 * 3. maxReconsumeTimes=3 → 第1次10s → 第2次30s → 第3次10m → DLQ
 * 4. DLQ 由 VideoAnalysisDlqConsumer 兜底，标记任务 FAILED
 */
@Component
@RocketMQMessageListener(
        topic = "video-analysis-topic",
        consumerGroup = "video-group",
        maxReconsumeTimes = 3
)
public class VideoAnalysisConsumer implements RocketMQListener<AnalysisTaskMsg> {

    @Autowired
    private AiService aiService;

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Override
    public void onMessage(AnalysisTaskMsg msg) {
        Long mediaId = msg.getMediaId();
        System.out.println("⚡ [MQ消费者] 收到任务 ID: " + mediaId);

        // 更新状态为处理中
        MediaFile file = mediaFileMapper.selectById(mediaId);
        if (file != null) {
            file.setAiSummary("[处理中] AI 分析正在进行...");
            mediaFileMapper.updateById(file);
        }

        // 不再 try-catch：异常直接向上抛给 RocketMQ
        // RocketMQ 会自动按延迟级别重试，3 次后进入 DLQ
        aiService.asyncAnalyze(mediaId);
    }
}
