CREATE TABLE IF NOT EXISTS ai_summary_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    media_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    result_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_summary_result_media_id (media_id),
    INDEX idx_ai_summary_result_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS rag_chunk_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    chunk_id VARCHAR(128) NOT NULL,
    video_id BIGINT NULL,
    title VARCHAR(512) NULL,
    source_type VARCHAR(64) NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    total_chunks INT NOT NULL DEFAULT 0,
    start_time BIGINT NULL,
    end_time BIGINT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    content MEDIUMTEXT NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rag_chunk_document_chunk_id (chunk_id),
    INDEX idx_rag_chunk_document_video_id (video_id),
    INDEX idx_rag_chunk_document_video_version_deleted (video_id, version, deleted),
    INDEX idx_rag_chunk_document_title (title),
    INDEX idx_rag_chunk_document_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS rag_video_version (
    video_id BIGINT PRIMARY KEY,
    current_version INT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
