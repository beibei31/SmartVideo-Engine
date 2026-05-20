package com.example.server.utils;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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
     * 上传音频临时文件到 MinIO，返回公网可访问的 URL（供百炼 ASR 使用）
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
        return publicEndpoint + "/" + bucketName + "/" + objectName;
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
}