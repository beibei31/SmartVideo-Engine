package com.example.server.rag.ingestion;

/**
 * 文档加载器接口
 * 不同格式（纯文本 / Markdown / PDF）各自实现
 */
public interface DocumentLoader {

    /**
     * 从 source 加载文档，返回纯文本内容
     *
     * @param source 文件路径或资源标识
     * @return 文档纯文本内容
     * @throws RuntimeException 加载失败时抛出
     */
    String load(String source);

    /**
     * 判断是否支持该 source
     *
     * @param source 文件路径或资源标识
     * @return true 表示可以处理
     */
    boolean supports(String source);
}
