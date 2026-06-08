package com.example.server.consumer;

import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者
 *
 * 当 AI 分析任务经过 3 次重试仍然失败后，RocketMQ 将消息投递到 DLQ。
 * 此消费者负责兜底：将任务标记为 FAILED，记录错误原因，方便人工排查。
 *
 * RocketMQ DLQ Topic 命名规则: %DLQ%{consumerGroup}
 */
@Component
@RocketMQMessageListener(
        topic = "%DLQ%video-group",
        consumerGroup = "video-dlq-group"
)
public class VideoAnalysisDlqConsumer implements RocketMQListener<MessageExt> {

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgBody = new String(messageExt.getBody());
        int reconsumeTimes = messageExt.getReconsumeTimes();
        String msgId = messageExt.getMsgId();

        System.err.println("☠️ [DLQ消费者] 死信消息到达！");
        System.err.println("   MsgId: " + msgId);
        System.err.println("   重试次数: " + reconsumeTimes);
        System.err.println("   消息体: " + msgBody);

        // 解析消息体获取 mediaId
        try {
            // 消息体格式: {"mediaId":123,"action":"START_ANALYSIS"}
            String json = msgBody.trim();
            long mediaId = extractMediaId(json);

            if (mediaId > 0) {
                MediaFile file = mediaFileMapper.selectById(mediaId);
                if (file != null) {
                    file.setAiSummary("❌ AI 分析失败：已重试 " + reconsumeTimes
                            + " 次仍未成功，请联系管理员。MsgId: " + msgId);
                    mediaFileMapper.updateById(file);
                    System.err.println("   [DLQ] 任务 " + mediaId + " 已标记为 FAILED");
                }
            }
        } catch (Exception e) {
            System.err.println("   [DLQ] 解析消息失败: " + e.getMessage());
        }
    }

    private long extractMediaId(String json) {
        try {
            // 简单的手动解析，避免引入额外的 JSON 依赖
            int start = json.indexOf("\"mediaId\":");
            if (start == -1) {
                start = json.indexOf("\"mediaId\" :");
            }
            if (start == -1) {
                return -1;
            }
            int colon = json.indexOf(":", start);
            if (colon == -1) {
                return -1;
            }
            String numStr = json.substring(colon + 1).trim()
                    .replaceAll("[^0-9]", "");
            return numStr.isEmpty() ? -1 : Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
