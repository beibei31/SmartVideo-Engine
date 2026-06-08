package com.example.server.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            //检查桶是否存在，不存在就创建
            boolean found = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                System.out.println("⚠️ MinIO 桶 [" + bucketName + "] 不存在，正在创建...");
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            System.out.println("  MinIO 配置成功，桶权限为 Private（通过预签名 URL 访问）！");
            return client;

        } catch (Exception e) {
            throw new RuntimeException("MinIO 初始化失败", e);
        }
    }
}