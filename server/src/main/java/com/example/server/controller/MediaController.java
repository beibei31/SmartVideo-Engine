package com.example.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.dto.ChunkCheckRequest;
import com.example.server.dto.ChunkCheckResponse;
import com.example.server.dto.ChunkMergeRequest;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.service.MediaService;
import com.example.server.utils.MinioUtils;
import com.example.server.utils.YtDlpUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/media")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MediaController {

    @Autowired(required = false)
    private MediaFileMapper mediaFileMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioUtils minioUtils;

    @Autowired
    private YtDlpUtils ytDlpUtils;

    @Autowired
    private MediaService mediaService;

    @PostMapping("/init-upload")
    public ResponseEntity<String> initUpload() {
        String uploadId = mediaService.initChunkedUpload();
        return ResponseEntity.ok(uploadId);
    }


    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "userId", required = false) Long userId) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Upload failed: file is empty");
        }
        if (mediaFileMapper == null) {
            return ResponseEntity.status(500).body("Upload failed: database not ready");
        }
        try {
            System.out.println("Uploading to MinIO...");
            String fileUrl = minioUtils.uploadFile(file);
            System.out.println("MinIO upload success, url: " + fileUrl);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setFilename(file.getOriginalFilename());
            mediaFile.setFilePath(fileUrl);
            mediaFile.setStatus("COMPLETED");
            mediaFile.setUploadTime(LocalDateTime.now());

            if (userId != null) {
                mediaFile.setUserId(userId);
            }

            mediaFileMapper.insert(mediaFile);

            if (userId != null) {
                String cacheKey = "media:list:user:" + userId;
                redisTemplate.delete(cacheKey);
                System.out.println("Cache cleared: " + cacheKey);
            }

            return ResponseEntity.ok("Upload success");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/upload-url")
    public org.springframework.http.ResponseEntity<String> uploadUrl(@RequestParam("url") String url,
                                                                     @RequestParam(value = "userId", required = false) Long userId) {
        File tempFile = null;
        try {
            if (url == null || url.isBlank()) {
                return org.springframework.http.ResponseEntity.badRequest().body("Upload failed: url is empty");
            }
            if (mediaFileMapper == null) {
                return org.springframework.http.ResponseEntity.status(500).body("Upload failed: database not ready");
            }
            System.out.println("Received upload url: " + url);

            tempFile = ytDlpUtils.downloadVideo(url);

            String fileUrl = minioUtils.uploadLocalFile(tempFile);

            MediaFile mediaFile = new MediaFile();
            mediaFile.setFilename("WEB_" + tempFile.getName());
            mediaFile.setFilePath(fileUrl);
            mediaFile.setStatus("COMPLETED");
            mediaFile.setUploadTime(LocalDateTime.now());

            if (userId != null) {
                mediaFile.setUserId(userId);
            }

            mediaFileMapper.insert(mediaFile);

            if (userId != null) {
                String cacheKey = "media:list:user:" + userId;
                redisTemplate.delete(cacheKey);
                System.out.println("Cache cleared: " + cacheKey);
            }

            return org.springframework.http.ResponseEntity.ok("Upload success");

        } catch (Exception e) {
            e.printStackTrace();
            return org.springframework.http.ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @GetMapping("/list")
    public List<MediaFile> getList(@RequestParam(value = "userId", required = false) Long userId) {
        String cacheKey = "media:list:user:" + (userId == null ? "anon" : userId);

        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                System.out.println("命中 Redis 缓存，直接返回！");
                return objectMapper.readValue(json, new TypeReference<List<MediaFile>>(){});
            }
        } catch (Exception e) {
            System.err.println("Redis 读取失败: " + e.getMessage());
        }

        QueryWrapper<MediaFile> query = new QueryWrapper<>();
        if (userId != null) {
            query.eq("user_id", userId);
        } else {
            return List.of();
        }
        List<MediaFile> list = mediaFileMapper.selectList(query.orderByDesc("id"));

        try {
            String jsonToWrite = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(cacheKey, jsonToWrite, 30, TimeUnit.MINUTES);
            System.out.println("已写入 Redis 缓存");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ==============================
    // 分片上传三接口
    // ==============================

    /**
     * POST /media/chunk/check — 秒传/断点续传/新任务 状态检查
     */
    @PostMapping("/chunk/check")
    public ResponseEntity<ChunkCheckResponse> chunkCheck(@RequestBody ChunkCheckRequest request) {
        try {
            ChunkCheckResponse response = mediaService.checkUpload(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * POST /media/chunk/upload — 上传单个分片
     */
    @PostMapping("/chunk/upload")
    public ResponseEntity<Map<String, Object>> chunkUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("md5") String md5,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("chunkMd5") String chunkMd5) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "分片数据为空"));
            }
            mediaService.uploadChunk(file.getInputStream(), md5, chunkIndex, totalChunks, chunkMd5);
            return ResponseEntity.ok(Map.of("success", true, "chunkIndex", chunkIndex));
        } catch (IllegalArgumentException e) {
            // MD5 校验失败
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * POST /media/chunk/merge — 合并所有分片为完整文件
     */
    @PostMapping("/chunk/merge")
    public ResponseEntity<Map<String, Object>> chunkMerge(@RequestBody ChunkMergeRequest request) {
        try {
            MediaFile mediaFile = mediaService.mergeChunks(
                    request.getMd5(),
                    request.getFilename(),
                    request.getFileSize(),
                    request.getTotalChunks(),
                    request.getUserId()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mediaId", mediaFile.getId(),
                    "fileUrl", mediaFile.getFilePath(),
                    "filename", mediaFile.getFilename()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * GET /media/access/{id} — 获取媒体文件的预签名访问 URL（1 小时有效）
     * MinIO bucket 为 private 模式，前端不能直接用 filePath，需要走后端拿预签名 URL
     */
    @GetMapping("/access/{id}")
    public ResponseEntity<Map<String, Object>> getAccessUrl(@PathVariable Long id) {
        try {
            MediaFile media = mediaFileMapper.selectById(id);
            if (media == null) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "文件不存在"));
            }

            // 从 filePath 中提取 MinIO objectName
            // filePath 格式: http://localhost:9000/media/uuid.mp4
            String objectName = extractObjectName(media.getFilePath());
            if (objectName == null) {
                return ResponseEntity.status(500)
                        .body(Map.of("error", "无法解析文件路径"));
            }

            String presignedUrl = minioUtils.getPresignedUrl(objectName, 3600);
            return ResponseEntity.ok(Map.of(
                    "url", presignedUrl,
                    "filename", media.getFilename() != null ? media.getFilename() : objectName,
                    "mediaId", media.getId()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    //删除接口
    @DeleteMapping("/delete")
    public String delete(@RequestParam("id") Long id,
                         @RequestParam(value = "userId", required = false) Long userId) {

        MediaFile media = mediaFileMapper.selectById(id);
        if (media == null) return "文件不存在";

        if (userId != null && !media.getUserId().equals(userId)) {
            return "无权删除他人的文件";
        }

        if (media.getFilePath() != null && media.getFilePath().startsWith("http")) {
            minioUtils.removeFile(media.getFilePath());
        }

        mediaFileMapper.deleteById(id);

        if (media.getUserId() != null) {
            String cacheKey = "media:list:user:" + media.getUserId();
            redisTemplate.delete(cacheKey);
            System.out.println("缓存已清除: " + cacheKey);
        }

        return "删除成功";
    }

    /**
     * 从 filePath 中提取 MinIO objectName
     * 输入: http://localhost:9000/media/uuid.mp4 → uuid.mp4
     * 输入: chunks/md5/00001 → chunks/md5/00001
     */
    private String extractObjectName(String filePath) {
        if (filePath == null) return null;
        // 尝试作为 URL 解析
        int bucketIdx = filePath.indexOf("/media/");
        if (bucketIdx >= 0) {
            return filePath.substring(bucketIdx + 7); // 跳过 "/media/"
        }
        // 如果已经是相对路径（分片等），直接返回
        if (filePath.contains("/") && !filePath.startsWith("http")) {
            return filePath;
        }
        return filePath.substring(filePath.lastIndexOf("/") + 1);
    }
}
