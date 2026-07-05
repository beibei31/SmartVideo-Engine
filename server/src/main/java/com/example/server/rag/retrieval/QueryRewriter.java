package com.example.server.rag.retrieval;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询重写器
 * 解决用户口语化、指代不明、多意图等问题，提升检索命中率
 *
 * 处理策略：
 *   1. 指代消解：把"它""这个""那个"替换为具体实体
 *   2. 口语转正式：把口语表达转为正式检索查询
 *   3. 关键词补充：补充同义词和相关术语
 *
 * 重写后的查询只用于向量数据库检索，Prompt 里仍然是用户原始问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriter {

    private final ChatLanguageModel chatLanguageModel;

    /** 是否启用 LLM 重写（关闭时直接返回原问题） */
    private static final boolean ENABLE_LLM_REWRITE = true;

    private static final PromptTemplate REWRITE_TEMPLATE = PromptTemplate.from(
            """
            你是一个搜索查询优化专家。你的任务是把用户的口语化问题改写成一个更适合向量检索的查询语句。

            【改写规则】
            1. 指代消解：把"它"、"这个"、"那个"、"其"等代词替换为具体实体名称
            2. 口语转正式：把口语化表达转为正式的书面表达
            3. 保留核心语义：不要添加问题中没有的信息，不要凭空猜测
            4. 简洁：只输出改写后的一句话，不要解释

            【对话历史】
            {{history}}

            【用户问题】
            {{question}}

            改写后的查询：""");

    private static final PromptTemplate REWRITE_NO_HISTORY_TEMPLATE = PromptTemplate.from(
            """
            你是一个搜索查询优化专家。把用户的口语化问题改写成适合向量检索的查询语句。

            【规则】
            1. 指代消解：替换代词
            2. 口语转正式
            3. 保留核心语义，不添加信息
            4. 只输出改写后的一句话

            【用户问题】
            {{question}}

            改写后的查询：""");

    /**
     * 重写查询
     *
     * @param rawQuestion 用户原始问题
     * @return 改写后的查询语句
     */
    public String rewrite(String rawQuestion) {
        return rewrite(rawQuestion, List.of());
    }

    /**
     * 带对话历史的重写
     *
     * @param rawQuestion 用户原始问题
     * @param history     最近的对话历史
     * @return 改写后的查询语句
     */
    public String rewrite(String rawQuestion, List<String> history) {
        // 快速检测：不需要重写的情况
        if (!ENABLE_LLM_REWRITE || !needsRewrite(rawQuestion)) {
            log.debug("查询无需重写，直接使用原文");
            return rawQuestion.trim();
        }

        try {
            Prompt prompt;
            if (history == null || history.isEmpty()) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("question", rawQuestion);
                prompt = REWRITE_NO_HISTORY_TEMPLATE.apply(vars);
            } else {
                Map<String, Object> vars = new HashMap<>();
                vars.put("history", String.join("\n", history));
                vars.put("question", rawQuestion);
                prompt = REWRITE_TEMPLATE.apply(vars);
            }

            String rewritten = chatLanguageModel.chat(prompt.text()).trim();

            // 去除模型多余的引号和空白
            rewritten = rewritten.replaceAll("^[\"']|[\"']$", "").trim();

            if (rewritten.isEmpty()) {
                return rawQuestion.trim();
            }

            log.debug("查询重写: [{}] → [{}]", rawQuestion, rewritten);
            return rewritten;

        } catch (Exception e) {
            log.warn("查询重写失败，回退到原始问题: {}", e.getMessage());
            return rawQuestion.trim();
        }
    }

    /**
     * 简单判断是否需要 LLM 重写
     * 包含代词、口语词的需要重写；已经清晰的问题不需要
     */
    private boolean needsRewrite(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }

        // 包含代词，需要指代消解
        String[] pronouns = {"它", "他", "她", "这个", "那个", "这些", "那些", "其", "该"};
        for (String p : pronouns) {
            if (question.contains(p)) {
                return true;
            }
        }

        // 太短的口语问题
        if (question.length() < 10) {
            return true;
        }

        return false;
    }
}
