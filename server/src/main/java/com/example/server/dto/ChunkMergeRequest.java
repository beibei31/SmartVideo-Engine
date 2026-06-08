package com.example.server.dto;

public class ChunkMergeRequest {
    private String md5;
    private String filename;
    private Long fileSize;
    private Integer totalChunks;
    private Long userId;

    public ChunkMergeRequest() {}

    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
