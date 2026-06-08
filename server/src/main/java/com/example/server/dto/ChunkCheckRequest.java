package com.example.server.dto;

import java.util.Set;

public class ChunkCheckRequest {
    private String md5;
    private String filename;
    private Long fileSize;
    private Long chunkSize;
    private Integer totalChunks;

    public ChunkCheckRequest() {}

    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getChunkSize() { return chunkSize; }
    public void setChunkSize(Long chunkSize) { this.chunkSize = chunkSize; }
    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
}
