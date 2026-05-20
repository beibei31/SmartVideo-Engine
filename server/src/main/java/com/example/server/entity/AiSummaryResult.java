package com.example.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_summary_result")
public class AiSummaryResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mediaId;
    private Long userId;
    private String resultJson;
    private LocalDateTime createdAt;
}
