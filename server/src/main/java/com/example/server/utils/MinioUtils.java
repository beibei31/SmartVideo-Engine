package com.example.server.utils;

import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class MinioUtils {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.public-endpoint:http://localhost:9000}")
    private String publicEndpoint;

    /**
     * 上传文件并返回访问 URL
     */
    public String uploadFile(MultipartFile file) throws Exception {
        // 1. 生成新文件名 (UUID防止重名)
        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + suffix;

        // 2. 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(newFilename)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }

        // 3. 拼接返回 Public 访问地址
        return endpoint + "/" + bucketName + "/" + newFilename;
    }

    /**
     * 【新增】从 MinIO 删除文件
     * @param fileUrl 文件的完整 URL
     */
    public void removeFile(String fileUrl) {
        try {
            // 解析文件名
            String objectName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

            // 调用 MinIO 删除
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );

            System.out.println(" MinIO 文件已删除: " + objectName);
        } catch (Exception e) {
            System.err.println(" MinIO 删除失败: " + e.getMessage());
        }
    }

    /**
     * 上传音频临时文件到 MinIO，返回预签名 URL（有效期 1 小时，供百炼 ASR 使用）
     */
    public String uploadTempAudio(java.io.File file) throws Exception {
        String objectName = "asr_temp_" + UUID.randomUUID() + ".mp3";
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.length(), -1)
                            .contentType("audio/mpeg")
                            .build()
            );
        }
        return getPresignedUrl(objectName, 3600);
    }

    /**
     * 生成预签名 GET URL（有效期默认 1 小时）
     */
    public String getPresignedUrl(String objectName, int expirySeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .method(Method.GET)
                        .expiry(expirySeconds)
                        .build()
        );
    }

    /**
     * 从 MinIO 下载文件到本地（通过 SDK 认证，Bucket 为 Private 也可访问）
     */
    public void downloadObject(String objectName, String localPath) throws Exception {
        minioClient.downloadObject(
                io.minio.DownloadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .filename(localPath)
                        .build()
        );
    }

    /**
     * 从 MinIO URL 中提取 objectName
     * 兼容格式: http://localhost:9000/media/uuid.mp4 和 /media/uuid.mp4
     */
    public String extractObjectName(String urlOrPath) {
        if (urlOrPath == null) return null;
        int bucketIdx = urlOrPath.indexOf("/media/");
        if (bucketIdx >= 0) {
            return urlOrPath.substring(bucketIdx + 7);
        }
        if (urlOrPath.contains("/") && !urlOrPath.startsWith("http")) {
            return urlOrPath;
        }
        return urlOrPath.substring(urlOrPath.lastIndexOf("/") + 1);
    }

    /**
     * 根据 URL 删除 MinIO 中的文件（兼容 public endpoint 和 local endpoint）
     */
    public void removeByUrl(String fileUrl) {
        try {
            String objectName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            System.out.println("[MinIO] temp file deleted: " + objectName);
        } catch (Exception e) {
            System.err.println("[MinIO] failed to delete temp file: " + e.getMessage());
        }
    }

    /**
     * 【新增】上传本地 File 对象到 MinIO
     */
    public String uploadLocalFile(java.io.File file) throws Exception {
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(file.getName()) // 文件名已经包含 UUID
                            .stream(inputStream, file.length(), -1)
                            .contentType("video/mp4") // 默认当 mp4 处理
                            .build()
            );
        }

        return endpoint + "/" + bucketName + "/" + file.getName();
    }

    // ==============================
    // 分片上传相关方法
    // ==============================

    /**
     * 生成分片在 MinIO 中的对象名
     * 格式: chunks/{md5}/{chunkIndex:05d}
     */
    public String getChunkObjectName(String md5, int chunkIndex) {
        return String.format("chunks/%s/%05d", md5, chunkIndex);
    }

    /**
     * 上传单个分片到 MinIO
     */
    public void uploadChunk(InputStream inputStream, String md5, int chunkIndex,
                            long chunkSize, String contentType) throws Exception {
        String objectName = getChunkObjectName(md5, chunkIndex);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, chunkSize, -1)
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .build()
        );
    }

    /**
     * 使用 MinIO composeObject API 合并所有分片为目标文件
     * 超过 1000 片时需分批 compose
     */
    public void composeChunks(String md5, int totalChunks, String targetObject) throws Exception {
        final int MAX_SOURCES = 1000;
        List<ComposeSource> sources = new ArrayList<>();

        for (int i = 0; i < totalChunks; i++) {
            sources.add(ComposeSource.builder()
                    .bucket(bucketName)
                    .object(getChunkObjectName(md5, i))
                    .build());

            // 每攒满 MAX_SOURCES 或到末尾时做一次 compose
            if (sources.size() == MAX_SOURCES || i == totalChunks - 1) {
                String tempObject = (i == totalChunks - 1 && sources.size() == totalChunks)
                        ? targetObject
                        : targetObject + ".part." + (i / MAX_SOURCES);

                minioClient.composeObject(
                        ComposeObjectArgs.builder()
                                .bucket(bucketName)
                                .object(tempObject)
                                .sources(sources)
                                .build()
                );

                // 如果用了临时对象，下一轮 compose 时用它作为输入的一部分
                if (!tempObject.equals(targetObject)) {
                    sources.clear();
                    sources.add(ComposeSource.builder()
                            .bucket(bucketName)
                            .object(tempObject)
                            .build());
                } else {
                    sources.clear();
                }
            }
        }

        // 清理中间临时合并文件
        if (totalChunks > MAX_SOURCES) {
            int partCount = (totalChunks - 1) / MAX_SOURCES;
            for (int p = 0; p < partCount; p++) {
                try {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(targetObject + ".part." + p)
                                    .build()
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 计算输入流的 MD5 值（使用 DigestInputStream 流式处理，内存友好）
     */
    public String computeFileMd5(InputStream inputStream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            md.update(buffer, 0, bytesRead);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 流式计算 MinIO 对象的 MD5（通过 getObject 流式读取，内存友好）
     */
    public String computeObjectMd5(String objectName) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 直接通过 objectName 删除 MinIO 文件
     */
    public void removeFileByObject(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            System.err.println("[MinIO] 删除文件失败 " + objectName + ": " + e.getMessage());
        }
    }

    /**
     * 合并完成后批量删除分片
     */
    public void removeChunks(String md5, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(getChunkObjectName(md5, i))
                                .build()
                );
            } catch (Exception e) {
                System.err.println("[MinIO] 清理分片失败 chunk " + i + ": " + e.getMessage());
            }
        }
    }
}