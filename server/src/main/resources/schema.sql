CREATE TABLE IF NOT EXISTS ai_summary_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    media_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    result_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_summary_result_media_id (media_id),
    INDEX idx_ai_summary_result_user_id (user_id)
);
