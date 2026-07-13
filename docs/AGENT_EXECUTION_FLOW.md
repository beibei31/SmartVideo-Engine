# Agent Execution Flow

本文以“用户在某个视频上提问：这个视频里 Redis 缓存击穿在哪里讲到？”为例，说明 SmartVideo Engine 当前受控 Agent 与 Scoped RAG 的实际执行流程。

## 1. 请求入口

前端 `ChatPanel` 将当前视频卡片的 `videoId` 和用户输入传给 `streamChat`，请求：

```http
POST /api/agent/chat/stream
Content-Type: application/json
```

```json
{
  "sessionId": "demo-session",
  "videoId": 42,
  "question": "这个视频里 Redis 缓存击穿在哪里讲到？"
}
```

`AgentController` 会做入口校验：

- `sessionId` 为空时生成 UUID。
- `videoId` 可解析为数字时进入 scoped RAG；非法字符串按未指定视频处理。
- `question` 为空白时不会进入 Agent，SSE 返回 `error` 和 `done`。

## 2. SSE 任务启动

`AgentController.streamChat` 创建 `SseEmitter`，并使用 `aiTaskExecutor` 执行后台任务。正常情况下事件顺序为：

1. `agent_status`
2. `tool_call`
3. `tool_result`
4. `final_answer`
5. `done`

如果执行过程中抛出异常，Controller 会发送：

1. `error`
2. `done`

然后正常 `complete()`，避免客户端无限等待。

## 3. Planner 决策

`AgentOrchestrator` 初始化 `AgentState`：

- `sessionId`
- `currentVideoId`
- 已执行 `steps`

随后调用 `PlannerService.plan(state, question)`。Planner 要求模型返回受控 JSON：

```json
{
  "thought": "需要定位相关时间片段",
  "action": "VideoSegmentLocatorTool",
  "arguments": {
    "query": "Redis 缓存击穿",
    "videoId": 42,
    "topK": 5
  }
}
```

允许的 action 白名单：

- `VideoSearchTool`
- `VideoSegmentLocatorTool`
- `VideoSummaryTool`
- `QuizTool`
- `KnowledgeQaTool`
- `FinalAnswer`

非法 JSON、缺失 action 或未知 action 都会降级为 `KnowledgeQaTool`，并补齐 `question` 与当前 `videoId`。

## 4. ToolExecutor 执行工具

`ToolExecutor` 根据 action 分发到对应工具。它负责：

- 将 Planner arguments 转成强类型 input。
- 校验 `videoId/topK/count` 等数值参数。
- 对缺失或非法参数返回 `ToolResult.failure`。
- 通过 `agent.tool.timeout-ms` 控制工具执行超时。

工具不会直接改变 Agent 架构；当前实现仍是受控 Planner + 白名单工具，不是完全自治 ReAct。

## 5. Scoped RAG 检索

视频内问答和定位类工具会进入 `RetrievalService`。当 `videoId=42` 时，检索会先查 `rag_video_version` 得到当前版本，例如 `version=3`，再构造 Milvus filter：

```text
videoId == "42" AND deleted == "false" AND version == "3"
```

检索步骤：

1. `QueryRewriter` 结合历史改写 query。
2. dense recall：Ollama embedding + Milvus。
3. sparse recall：BM25 内存索引。
4. hybrid 模式下用 `RrfFuser` 融合 dense 和 BM25 排名。
5. `RagChunkValidator` 批量查询 MySQL，仅保留 active chunk。
6. `RerankerService` 取最终 topK。

MySQL 是 chunk 原文和版本状态的 source of truth。Milvus metadata filter 是第一层约束，MySQL active 校验是进入 LLM 前的第二层约束。

## 6. 生成与返回

如果工具返回：

- `KnowledgeQaResult`：直接取 `answer` 作为最终答案。
- `VideoSummaryResult`：直接取 `summary`。
- `QuizResult`：直接取 `quiz`。
- `VideoSegmentLocatorResult`：格式化为时间段列表。

如果工具不能直接产生最终答案，`AgentOrchestrator` 会把 `AgentStep` 加入状态，继续下一轮 Planner。达到 `agent.max-steps` 后停止，返回“已达到最大执行步数”。

## 7. 可观测性

前端会展示：

- Planner 选择的工具。
- 工具执行结果。
- 最终答案。
- 错误事件。

后端测试覆盖 Planner 降级、maxSteps 终止、ToolExecutor 参数错误和超时、RAG scoped filter、RRF 去重稳定性、MySQL active chunk 校验与 SSE 异常结束。
