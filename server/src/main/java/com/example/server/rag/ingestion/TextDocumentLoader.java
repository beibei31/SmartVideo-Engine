package com.example.server.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 纯文本加载器
 * 支持 .txt / .md / 直接文本字符串
 */
@Slf4j
@Component
public class TextDocumentLoader implements DocumentLoader {

    private static final String[] SUPPORTED_EXTENSIONS = {
            ".txt", ".md", ".markdown", ".text"
    };

    @Override
    public String load(String source) {
        Path path = Paths.get(source);

        if (!Files.exists(path)) {
            throw new RuntimeException("文件不存在: " + source);
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            log.info("文档加载成功: {} ({} 字符)", source, content.length());
            return content;
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + source, e);
        }
    }

    @Override
    public boolean supports(String source) {
        if (source == null) {
            return false;
        }
        String lower = source.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
