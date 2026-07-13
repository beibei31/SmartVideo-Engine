# Failure And Fallback

本文记录当前 SmartVideo Engine Agent 与 Scoped RAG 核心链路中已经实现的失败处理与降级行为。

## Planner

| 场景 | 行为 | 测试 |
| --- | --- | --- |
| 模型返回非法 JSON | 降级为 `KnowledgeQaTool`，补齐 `question` 和当前 `videoId` | `PlannerServiceTest.returnsKnowledgeQaFallbackWhenPlannerJsonIsInvalid` |
| 模型返回未知 action | 白名单校验失败，降级为 `KnowledgeQaTool` | `PlannerServiceTest.rejectsActionOutsideWhitelist` |
| JSON 被 markdown fence 包裹 | 提取 fence 内 JSON 后解析 | `PlannerServiceTest.parsesJsonWrappedInMarkdownCodeFence` |

Planner 不会执行任意工具；所有 action 必须在白名单内。

## Agent Orchestrator

| 场景 | 行为 | 测试 |
| --- | --- | --- |
| Planner 返回 `FinalAnswer` | 不调用工具，直接返回答案 | `AgentOrchestratorTest.stopsWhenPlannerReturnsFinalAnswer` |
| 工具返回可直接回答的数据 | 立即结束循环并返回最终答案 | `AgentOrchestratorTest.returnsImmediatelyWhenKnowledgeQaToolProducesAnswer` |
| Planner 始终不给最终答案 | 达到 `agent.max-steps` 后停止 | `AgentOrchestratorTest.stopsAtMaxStepsWhenPlannerNeverFinalizes` |

## ToolExecutor

| 场景 | 行为 | 测试 |
| --- | --- | --- |
| 未知工具 action | 返回 `ToolResult.failure` | `ToolExecutorTest.rejectsUnknownToolAction` |
| arguments 为 null | 返回 `Invalid tool arguments: arguments must not be null` | `ToolExecutorTest.returnsClearFailureWhenToolArgumentsAreMissing` |
| 数值参数非法 | 返回字段级参数错误，例如 `topK must be a number` | `ToolExecutorTest.returnsClearFailureWhenNumericArgumentIsInvalid` |
| 工具执行超过超时 | 返回 `Tool execution timed out after ...ms` | `ToolExecutorTest.returnsTimeoutFailureWhenToolDoesNotCompleteInTime` |
| 工具内部异常 | 返回 `Tool execution failed: ...` | 代码路径在 `ToolExecutor.execute` |

超时由 `agent.tool.timeout-ms` 控制，默认 30000ms。

## Scoped Retrieval

| 场景 | 行为 | 测试 |
| --- | --- | --- |
| 请求指定 `videoId` 且有 currentVersion | Milvus filter 包含 `videoId`、`deleted=false`、`version=currentVersion` | `RetrievalServiceTest.scopedSearchAddsMilvusFilterForCurrentVideoVersion` |
| hybrid 检索 dense 依赖失败 | dense 返回空列表，继续走 BM25 + RRF + MySQL 校验 | `RetrievalServiceTest.hybridSearchFallsBackToBm25WhenDenseSearchFails` |
| chunk 已删除或旧版本 | MySQL active chunk 批量校验过滤掉 | `RagChunkValidatorTest.keepsOnlyChunkIdsThatMysqlMarksAsActiveInOneBatch` |
| dense 和 BM25 返回同一 chunk | RRF 按 chunkId 去重，并保留稳定排序 | `RrfFuserTest.fusedResultsAreStableAndDeduplicatedByChunkId` |

纯 dense 检索不吞掉 Milvus/Ollama 异常，便于发现基础设施故障。

## Redis Chat Memory

| 场景 | 行为 | 测试 |
| --- | --- | --- |
| 消息数刚超过窗口但未超过摘要触发阈值 | 不摘要，直接保存 | `ChatMemoryServiceTest.doesNotSummarizeImmediatelyAfterWindowIsSlightlyExceeded` |
| 超过摘要触发阈值 | 尝试生成摘要并压缩窗口 | `ChatMemoryServiceTest.summarizesOnlyWhenTriggerThresholdIsExceededAndKeepsWindowBounded` |
| TTL 配置小于 1 天 | 使用最小 1 天 TTL | `ChatMemoryServiceTest.usesAtLeastOneDayTtlWhenConfiguredTtlIsTooSmall` |
| Redis 中历史 JSON 损坏 | 返回空历史并记录 warn | 代码路径在 `ChatMemoryService.loadMessages` |
| 摘要模型异常 | 丢弃溢出的旧消息，不阻断保存 | 代码路径在 `compressWithSummary` |

## SSE

| 场景 | 行为 | 测试 |
| --- | --- | --- |
| 空问题 | 发送 `error` 和 `done`，不调用 orchestrator | `AgentControllerTest.streamChatRejectsBlankQuestionWithErrorAndDoneBeforeCallingOrchestrator` |
| Orchestrator 抛异常 | 发送 `error` 和 `done`，然后正常 complete | `AgentControllerTest.streamChatSendsErrorAndDoneThenCompletesNormallyWhenOrchestratorFails` |
| 客户端断开导致 send 失败 | `sendEvent` 捕获 `IOException` 并忽略，后台任务随后结束或继续执行到 complete | 代码路径在 `AgentController.sendEvent` |

当前实现没有伪造真实 Milvus、Redis、MQ、LLM 或 ASR 运行结果；测试使用 mock 和替身验证本地逻辑。
