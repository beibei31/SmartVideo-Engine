package com.example.server.rag.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_chunk_document")
public class RagChunkDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String chunkId;
    private Long videoId;
    private String title;
    private String sourceType;
    private Integer chunkIndex;
    private Integer totalChunks;
    private Long startTime;
    private Long endTime;
    private Boolean deleted;
    private Integer version;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
