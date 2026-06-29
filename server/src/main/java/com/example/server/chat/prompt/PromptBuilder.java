package com.example.server.chat.prompt;

import com.example.server.rag.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 组装器
 *
 * 将 System Prompt + 检索上下文 + 对话历史 + 用户问题拼接为完整 Prompt
 *
 * 结构：
 *   [System Prompt] 角色设定 + 行为规则
 *   [对话历史] 最近 N 轮对话（含摘要）
 *   [检索上下文] Top-K chunk + 来源标注
 *   [用户问题] 原始提问
 */
@Slf4j
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的视频内容分析助手。用户会向你提问关于已上传视频的内容，你会基于提供的参考文档和你的知识来回答。

            【回答原则】
            1. 优先使用参考文档中的信息来回答。如果文档中有相关信息，请直接提取和总结。
            2. 当文档信息不足以完整回答时，可以结合你的常识进行合理的推断和补充，但要自然地说明哪些是文档内容、哪些是你的理解。
            3. 回答要自然流畅，像一个真正的人在对话，而不是机器在汇报。不要使用编号引用格式。
            4. 如果文档完全不相关，也要尽力给出有帮助的回应，例如建议用户上传相关视频或换个问法。
            5. 用清晰的中文回答，简洁但不失温度。
            """;

    /**
     * 构建完整 Prompt（不带对话历史）
     */
    public String build(String userQuestion, List<SearchResult> contexts) {
        return build(userQuestion, contexts, List.of());
    }

    /**
     * 构建完整 Prompt（带对话历史）
     */
    public String build(String userQuestion, List<SearchResult> contexts, List<String> history) {
        StringBuilder sb = new StringBuilder();

        // 1. System Prompt
        sb.append(SYSTEM_PROMPT).append("\n\n");

        // 2. 对话历史（如果有）
        if (history != null && !history.isEmpty()) {
            sb.append("【对话历史】\n");
            for (String h : history) {
                sb.append(h).append("\n");
            }
            sb.append("\n");
        }

        // 3. 检索上下文
        sb.append("【参考文档】\n");
        if (contexts == null || contexts.isEmpty()) {
            sb.append("（无相关参考文档）\n");
        } else {
            for (int i = 0; i < contexts.size(); i++) {
                SearchResult ctx = contexts.get(i);
                sb.append("--- 文档片段 [").append(i + 1).append("] ---\n");
                sb.append("来源: ").append(
                        ctx.getSourceTitle() != null ? ctx.getSourceTitle() : "未知"
                ).append("\n");
                sb.append("内容: ").append(ctx.getContent()).append("\n\n");
            }
        }

        // 4. 用户问题
        sb.append("【用户问题】\n");
        sb.append(userQuestion).append("\n");

        log.debug("Prompt 构建完成, 上下文 chunk 数: {}",
                contexts != null ? contexts.size() : 0);
        return sb.toString();
    }

    /**
     * 仅构建 System Prompt（用于 ChatMemory 场景，由框架拼历史）
     */
    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }
}
