package com.example.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.dto.ChunkCheckRequest;
import com.example.server.dto.ChunkCheckResponse;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.utils.MinioUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MediaService {

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MinioUtils minioUtils;

    @Autowired
    @Qualifier("aiTaskExecutor")
    private Executor aiTaskExecutor;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    private final String UPLOAD_DIR = "D:/Project/MediaApp/uploads/";
    private static final String CHUNK_UPLOAD_KEY_PREFIX = "upload:task:";
    private static final String MERGE_LOCK_PREFIX = "merge:lock:";
    private static final long CHUNK_TTL_HOURS = 24;

    public MediaService() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    public String initChunkedUpload() {
        String uploadId = UUID.randomUUID().toString();
        String redisKey = "upload:chunked:" + uploadId;
        redisTemplate.opsForValue().set(redisKey, "INIT", 1, TimeUnit.DAYS);
        return uploadId;
    }

    // ==============================
    // 分片上传核心业务
    // ==============================

    /**
     * 检查上传状态：秒传 / 断点续传 / 新任务
     */
    public ChunkCheckResponse checkUpload(ChunkCheckRequest req) {
        String md5 = req.getMd5();

        // 1. 秒传检查：查 MySQL file_md5 唯一索引
        QueryWrapper<MediaFile> query = new QueryWrapper<>();
        query.eq("file_md5", md5);
        MediaFile existing = mediaFileMapper.selectOne(query);
        if (existing != null) {
            return ChunkCheckResponse.completed(existing.getFilePath(), existing.getId());
        }

        // 2. 断点续传检查：查 Redis Set
        String redisKey = CHUNK_UPLOAD_KEY_PREFIX + md5;
        Set<String> members = redisTemplate.opsForSet().members(redisKey);
        if (members != null && !members.isEmpty()) {
            Set<Integer> uploadedChunks = members.stream()
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
            return ChunkCheckResponse.partial(uploadedChunks);
        }

        // 3. 全新任务：Redis Key 在首片上传时由 uploadChunk 自动创建并设 TTL
        return ChunkCheckResponse.newTask();
    }

    /**
     * 上传单个分片：先落盘 MinIO，后记账 Redis（先落盘后记账策略）
     */
    public void uploadChunk(InputStream inputStream, String md5, int chunkIndex,
                            int totalChunks, String chunkMd5) throws Exception {
        String redisKey = CHUNK_UPLOAD_KEY_PREFIX + md5;

        // 1. 校验分片 MD5（需要将流读两次：先读一次算 MD5，再读一次上传）
        // 策略：先读入内存计算 MD5，验证通过后再上传。分片只有 5MB，内存可接受。
        byte[] chunkData = inputStream.readAllBytes();

        // 计算实际 MD5
        String actualMd5 = computeMd5(chunkData);

        if (!actualMd5.equalsIgnoreCase(chunkMd5)) {
            throw new IllegalArgumentException(
                    String.format("分片 %d MD5 校验失败！前端: %s, 服务端: %s", chunkIndex, chunkMd5, actualMd5));
        }

        // 2. 上传分片到 MinIO（先落盘）
        minioUtils.uploadChunk(
                new java.io.ByteArrayInputStream(chunkData),
                md5, chunkIndex, chunkData.length,
                "application/octet-stream"
        );

        // 3. Redis 记录分片序号（后记账）+ 刷新 TTL
        redisTemplate.opsForSet().add(redisKey, String.valueOf(chunkIndex));
        redisTemplate.expire(redisKey, CHUNK_TTL_HOURS, TimeUnit.HOURS);

        System.out.println("[分片上传] MD5=" + md5 + " chunk=" + chunkIndex
                + "/" + totalChunks + " 上传成功");
    }

    /**
     * 合并分片：SETNX 锁 + 完整性校验 + MinIO compose + MD5 校验 + DB 写入
     */
    public MediaFile mergeChunks(String md5, String filename, long fileSize,
                                  int totalChunks, Long userId) throws Exception {
        String lockKey = MERGE_LOCK_PREFIX + md5;
        String redisKey = CHUNK_UPLOAD_KEY_PREFIX + md5;

        // 1. SETNX 合并锁（幂等保护，30s 过期防止死锁）
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "merging", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockAcquired)) {
            throw new IllegalStateException("合并进行中，请勿重复提交");
        }

        try {
            // 2. 二次查 MySQL（可能已被另一个请求合并完成）
            QueryWrapper<MediaFile> query = new QueryWrapper<>();
            query.eq("file_md5", md5);
            MediaFile existing = mediaFileMapper.selectOne(query);
            if (existing != null) {
                System.out.println("[合并] 文件已存在，跳过合并: " + md5);
                return existing;
            }

            // 3. 校验 Redis 分片完整性
            Set<String> members = redisTemplate.opsForSet().members(redisKey);
            if (members == null || members.size() != totalChunks) {
                int uploaded = (members == null) ? 0 : members.size();
                throw new IllegalStateException(
                        String.format("分片不完整！期望 %d 片，已上传 %d 片", totalChunks, uploaded));
            }

            // 4. 生成目标文件名
            String suffix = "";
            if (filename != null && filename.contains(".")) {
                suffix = filename.substring(filename.lastIndexOf("."));
            }
            String targetObject = UUID.randomUUID().toString() + suffix;

            // 5. 调用 MinIO composeObject 合并
            System.out.println("[合并] 开始合并 " + totalChunks + " 个分片 -> " + targetObject);
            minioUtils.composeChunks(md5, totalChunks, targetObject);
            System.out.println("[合并] composeObject 完成: " + targetObject);

            // 5.5 合并后完整文件 MD5 校验（流式计算，内存友好）
            String mergedMd5 = minioUtils.computeObjectMd5(targetObject);
            if (!md5.equalsIgnoreCase(mergedMd5)) {
                // 校验失败：删除已合并文件，清理分片，报错
                minioUtils.removeFileByObject(targetObject);
                throw new IllegalStateException(
                        String.format("合并文件 MD5 校验失败！前端: %s, 服务端: %s", md5, mergedMd5));
            }
            System.out.println("[合并] 完整文件 MD5 校验通过: " + md5);

            // 6. 写入 MySQL 记录
            String fileUrl = endpoint + "/" + bucketName + "/" + targetObject;
            MediaFile mediaFile = new MediaFile();
            mediaFile.setFilename(filename);
            mediaFile.setFilePath(fileUrl);
            mediaFile.setStatus("COMPLETED");
            mediaFile.setUploadTime(LocalDateTime.now());
            mediaFile.setFileMd5(md5);
            mediaFile.setFileSize(fileSize);
            mediaFile.setChunkCount(totalChunks);
            if (userId != null) {
                mediaFile.setUserId(userId);
            }

            mediaFileMapper.insert(mediaFile);
            System.out.println("[合并] MySQL 记录写入成功，mediaId=" + mediaFile.getId());

            // 7. 清理 Redis + MinIO 分片
            redisTemplate.delete(redisKey);
            // 异步清理分片（使用业务线程池，不阻塞返回）
            aiTaskExecutor.execute(() -> minioUtils.removeChunks(md5, totalChunks));

            // 8. 清除用户列表缓存
            if (userId != null) {
                redisTemplate.delete("media:list:user:" + userId);
            }

            return mediaFile;

        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 计算字节数组的 MD5
     */
    private String computeMd5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public String convertVideoToAudio(MultipartFile file) throws IOException, InterruptedException {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFilename(file.getOriginalFilename());
        mediaFile.setStatus("PROCESSING"); //状态：处理中
        mediaFile.setUploadTime(LocalDateTime.now());
        mediaFile.setFilePath(""); //暂时为空

        //这一步执行后，MySQL 里就会多一行数据
        mediaFileMapper.insert(mediaFile);

        // --- 下面是原有的文件处理逻辑 ---
        String fileId = UUID.randomUUID().toString();
        String inputPath = UPLOAD_DIR + fileId + "_input.mp4";
        String outputPath = UPLOAD_DIR + fileId + "_output.mp3";

        File inputFile = new File(inputPath);
        file.transferTo(inputFile);

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        command.add("-vn");
        command.add("-acodec");
        command.add("libmp3lame");
        command.add("-q:a");
        command.add("2");
        command.add(new File(outputPath).getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        if (process.waitFor() == 0) {
            inputFile.delete(); // 删掉原视频

            // --- 数据库操作：更新状态为完成 ---
            mediaFile.setStatus("COMPLETED");
            mediaFile.setFilePath(outputPath);
            mediaFileMapper.updateById(mediaFile); // 根据 ID 更新这一行

            return outputPath;
        } else {
            // --- 数据库操作：记录失败 ---
            mediaFile.setStatus("FAILED");
            mediaFileMapper.updateById(mediaFile);
            throw new RuntimeException("FFmpeg 转换失败");
        }
    }
}
