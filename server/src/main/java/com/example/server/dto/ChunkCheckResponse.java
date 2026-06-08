package com.example.server.dto;

import java.util.Set;

public class ChunkCheckResponse {
    // "new" | "partial" | "completed"
    private String status;
    // 秒传标志
    private boolean deduplicated;
    // 已上传的分片序号（仅 partial 时返回）
    private Set<Integer> uploadedChunks;
    // 秒传时返回已有文件URL
    private String fileUrl;
    // 秒传时返回已有的 mediaId
    private Long mediaId;

    public ChunkCheckResponse() {}

    public static ChunkCheckResponse completed(String fileUrl, Long mediaId) {
        ChunkCheckResponse r = new ChunkCheckResponse();
        r.status = "completed";
        r.deduplicated = true;
        r.fileUrl = fileUrl;
        r.mediaId = mediaId;
        return r;
    }

    public static ChunkCheckResponse partial(Set<Integer> uploadedChunks) {
        ChunkCheckResponse r = new ChunkCheckResponse();
        r.status = "partial";
        r.deduplicated = false;
        r.uploadedChunks = uploadedChunks;
        return r;
    }

    public static ChunkCheckResponse newTask() {
        ChunkCheckResponse r = new ChunkCheckResponse();
        r.status = "new";
        r.deduplicated = false;
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDeduplicated() { return deduplicated; }
    public void setDeduplicated(boolean deduplicated) { this.deduplicated = deduplicated; }
    public Set<Integer> getUploadedChunks() { return uploadedChunks; }
    public void setUploadedChunks(Set<Integer> uploadedChunks) { this.uploadedChunks = uploadedChunks; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long mediaId) { this.mediaId = mediaId; }
}
