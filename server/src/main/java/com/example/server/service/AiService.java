package com.example.server.service;

import com.example.server.context.TokenUsageContext;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.service.TokenUsageService;
import com.example.server.strategy.AiAnalysisStrategy;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    @Qualifier("defaultAiStrategy")
    private AiAnalysisStrategy aiAnalysisStrategy;

    // 【关键】必须注入 Redis 工具！
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private AiSummaryResultService aiSummaryResultService;

    @Autowired
    private TokenUsageService tokenUsageService;


    public void asyncAnalyze(Long mediaId) {
        System.out.println(" [线程池] 开始处理任务，ID: " + mediaId);

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) return;

        // 消费者端分布式锁：基于 mediaId 防重复消费
        // WatchDog 自动续期，处理完成后释放
        String lockKey = "lock:analyze:" + mediaId;
        RLock lock = redissonClient.getLock(lockKey);
        if (!lock.tryLock()) {
            System.out.println(" [线程池] 任务已被其他消费者处理中，跳过: mediaId=" + mediaId);
            return;
        }

        try {
            // 拿到锁后二次检查：可能上一个持有者刚处理完
            MediaFile latest = mediaFileMapper.selectById(mediaId);
            if (latest != null && latest.getAiSummary() != null
                    && !latest.getAiSummary().contains("处理中")
                    && !latest.getAiSummary().contains("等待调度")
                    && !latest.getAiSummary().startsWith("[MQ]")) {
                System.out.println(" [线程池] 任务已完成，跳过: mediaId=" + mediaId);
                return;
            }

            // 额度检查：MQ 异步路径也必须校验
            Long userId = mediaFile.getUserId();
            String userIdStr = (userId == null) ? "anon" : String.valueOf(userId);
            if (!tokenUsageService.hasQuota(userId)) {
                System.err.println("[MQ消费者] Token 额度已耗尽，任务取消: mediaId=" + mediaId);
                mediaFile.setAiSummary("今日 AI 算力已耗尽，请明天再试");
                mediaFileMapper.updateById(mediaFile);
                redisTemplate.delete("media:list:user:" + userIdStr);
                return;
            }

            TokenUsageContext.setUserId(mediaFile.getUserId());
            // 1. 语音转文字
            String text = aiAnalysisStrategy.transcribe(mediaFile.getFilePath());
            mediaFile.setTranscriptText(text);

            // 2. 智能总结（直接用已转写文字，不再重复ASR）
            String summary = aiAnalysisStrategy.generateSummaryFromText(text);
            mediaFile.setAiSummary(summary);
            aiSummaryResultService.saveResult(mediaFile.getId(), mediaFile.getUserId(), summary);

            // 3. 保存数据库
            mediaFileMapper.updateById(mediaFile);

            // 4. 清除缓存
            String cacheKey = "media:list:user:" + userIdStr;
            Boolean deleteResult = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleteResult)) {
                System.out.println(" [线程池] 缓存清除成功！Key: " + cacheKey);
            } else {
                System.out.println(" [线程池] 缓存不存在或清除失败 (但这不影响新数据写入)，Key: " + cacheKey);
            }

            System.out.println(" [线程池] 任务全部完成，前端轮询将在下一次命中新数据。");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(" [线程池] 任务失败: " + e.getMessage());

            // 失败也要删缓存
            String userIdStr = (mediaFile.getUserId() == null) ? "anon" : String.valueOf(mediaFile.getUserId());
            redisTemplate.delete("media:list:user:" + userIdStr);
            // 异常抛出，触发 RocketMQ 重试
            throw new RuntimeException(e);
        } finally {
            TokenUsageContext.clear();
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }



    //异步提取全文 (专门负责提取文字)
    @Async("aiTaskExecutor")
    public void asyncTranscribe(Long mediaId) {
        System.out.println(" [线程池] 开始全文提取任务，ID: " + mediaId);

        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) return;

        // 额度检查：MQ 异步路径也必须校验
        Long userId = mediaFile.getUserId();
        String userIdStr = (userId == null) ? "anon" : String.valueOf(userId);
        if (!tokenUsageService.hasQuota(userId)) {
            System.err.println("[MQ消费者] Token 额度已耗尽，转写任务取消: mediaId=" + mediaId);
            mediaFile.setTranscriptText("今日 AI 算力已耗尽，请明天再试");
            mediaFileMapper.updateById(mediaFile);
            redisTemplate.delete("media:list:user:" + userIdStr);
            return;
        }

        try {
            TokenUsageContext.setUserId(mediaFile.getUserId());
            //只做语音转文字
            String text = aiAnalysisStrategy.transcribe(mediaFile.getFilePath());
            mediaFile.setTranscriptText(text);

            //保存数据库
            mediaFileMapper.updateById(mediaFile);

            //强制删除 Redis 缓存
            String cacheKey = "media:list:user:" + userIdStr;
            redisTemplate.delete(cacheKey);

            System.out.println(" [线程池] 全文提取完成，缓存已清除！Key: " + cacheKey);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(" [线程池] 提取失败: " + e.getMessage());
        } finally {
            TokenUsageContext.clear();
        }
    }
}
