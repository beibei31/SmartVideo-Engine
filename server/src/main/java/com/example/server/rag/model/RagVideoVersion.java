package com.example.server.rag.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_video_version")
public class RagVideoVersion {

    @TableId
    private Long videoId;

    private Integer currentVersion;
    private LocalDateTime updatedAt;
}
