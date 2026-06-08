-- ============================================
-- 分片上传 + MD5 去重 数据库迁移脚本
-- ============================================

ALTER TABLE media_files
    ADD COLUMN file_md5 VARCHAR(32) NULL COMMENT '文件MD5指纹',
    ADD COLUMN file_size BIGINT NULL COMMENT '文件大小(字节)',
    ADD COLUMN chunk_count INT NULL COMMENT '总分片数';

-- 秒传去重索引（同一 MD5 的文件只存一份）
CREATE UNIQUE INDEX idx_file_md5 ON media_files(file_md5);
