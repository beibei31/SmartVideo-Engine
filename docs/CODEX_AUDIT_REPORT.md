# SmartVideo-Engine 工程审查报告

审查日期：2026-07-12

## 1. 项目调用链

### 1.1 视频上传与分析

1. 前端 `client/src/App.vue` 通过普通上传、URL 导入或分片上传调用后端 `/media/*` 接口。
2. `MediaController` 调用 `MediaService`、`MinioUtils`、`YtDlpUtils`，将视频对象保存到 MinIO，并将媒体元数据写入 MySQL `media_file`。
3. 用户点击“提取文字”时调用 `/debug/transcribe?id={mediaId}`，`AiService.asyncTranscribe` 使用 `AiAnalysisStrategy` 做 ASR，并把 `transcriptText` 写回 `media_file`。
4. 用户点击“AI 智能总结”时调用 `/debug/ai?id={mediaId}`，`DebugController` 写入 MQ 任务 `video-analysis-topic`。
5. `VideoAnalysisConsumer` 消费 `AnalysisTaskMsg`，调用 `AiService.asyncAnalyze`。
6. `AiService` 用 Redisson 锁做重复消费保护，调用 `AliyunDeepSeekStrategy`：
   - 从 MinIO 下载私有视频或读取本地文件。
   - 用 FFmpeg 抽取 mp3。
   - 上传临时音频到 MinIO 生成预签名 URL。
   - 调用阿里云 ASR 得到转写文本。
   - 调用 DeepSeek/OpenAI 兼容模型生成摘要。
7. 分析结果写回 `MediaFile.transcriptText` 和 `MediaFile.aiSummary`，并清理 Redis 媒体列表缓存。

### 1.2 RAG 入库

1. 前端点击“问这个视频”后调用 `POST /api/rag/ingest/{mediaId}`。
2. `RagController.ingestByMediaId` 读取 `MediaFile.transcriptText`。
3. `IngestionService` 使用 `RecursiveTextSplitter` 切分 chunk。
4. 每个 chunk：
   - 调用 `EmbeddingService` 生成 embedding。
   - 写入 `MilvusEmbeddingStore`。
   - 写入 MySQL `rag_chunk_document`。
   - 写入 `Bm25Index` 内存索引。
5. 同一视频重复 ingest 时，MySQL 旧版本 chunk 标记为 `deleted=true`，`rag_video_version` 发布最新版本。

### 1.3 Agent 问答与 SSE

1. 前端 `ChatPanel` 调用 `streamChat`，向 `/api/agent/chat/stream` 发送 `question/sessionId/videoId`。
2. `AgentController` 将请求转成 `AgentRequest`，使用 `aiTaskExecutor` 执行 SSE 后台任务。
3. `AgentOrchestrator` 调用 `PlannerService`，让 LLM 输出受控 JSON action。
4. `ToolExecutor` 根据 action 调用：
   - `VideoSearchTool`
   - `VideoSegmentLocatorTool`
   - `VideoSummaryTool`
   - `QuizTool`
   - `KnowledgeQaTool`
5. RAG 工具进入 `RetrievalService`：query rewrite -> dense 检索 -> BM25 检索 -> RRF 融合 -> MySQL active chunk 校验 -> rerank。
6. `GenerationService` 基于上下文生成最终答案。
7. 后端发送 SSE 事件：`agent_status`、`tool_call`、`tool_result`、`final_answer`、`done`、`error`。

## 2. 发现的问题

### P0/P1 已修复

1. 后端缺少标准 `application.properties`，只有本地 `application.properties.local`。Spring Boot 默认启动会缺少 DeepSeek、Milvus、Embedding、MinIO 等关键配置。
2. `.gitignore` 忽略标准 `application.properties`，但没有忽略 `.local`；本地文件中出现明文 API Key，存在误提交风险。
3. `AgentController` SSE 使用 `CompletableFuture.runAsync` 默认公共线程池，和项目已有 `aiTaskExecutor` 不一致，真实并发下不可控。
4. `AgentController` 对非法字符串 `videoId` 直接 `Long.parseLong`，可由请求触发 500。
5. hybrid RAG 检索中，dense embedding/Milvus 失败会直接中断，BM25 无法兜底。
6. `/debug/token-usage` 的 `dailyQuota` 和 `remaining` 写死为 `50000`，与 `ai.token.daily-quota` 配置不一致。
7. 前端 `v-html + marked.parse` 直接渲染模型输出，存在原始 HTML 注入风险。
8. 一次临时前端构建污染了 `client/index.html`，入口被改为构建产物路径；已恢复为 Vite 源码入口。
9. `client/dist` 在当前 Windows 环境中被旧产物占用，导致默认 `npm run build` 无法满足完成标准。

### P2/P3 未修复或仅记录

1. 前端 `App.vue` 多处硬编码 `http://localhost:9090`，部署到非本机环境需要统一抽取 API base URL。
2. `DebugController` 混合了承载演示、调试和业务触发的职责，正式生产应拆出权限受控的业务 API。
3. 后端仍有较多 `System.out.println` 和 `printStackTrace`，建议后续统一替换为结构化日志。
4. `MediaService` 中历史 `convertVideoToAudio` 仍依赖本地上传目录语义，虽然当前主链路走 MinIO，但该方法生产化前应继续收敛配置和清理策略。
5. 没有真实启动 MySQL、Redis、RocketMQ、Milvus、Ollama、MinIO、DeepSeek、阿里云 ASR 做端到端运行；本次使用 mock 和构建验证本地逻辑。

## 3. 已修复内容

1. 新增 `server/src/main/resources/application.properties`，提供安全默认值和环境变量占位。
2. 调整 `.gitignore`：
   - 不再忽略标准 `application.properties`。
   - 忽略 `application.properties.local`、`*.env.local` 和前端临时构建目录。
3. `AgentController`：
   - 注入 `@Qualifier("aiTaskExecutor") Executor`。
   - SSE 后台任务使用项目线程池。
   - 非法 `videoId` 降级为 `null`，避免请求级 500。
4. `RetrievalService`：
   - hybrid 模式下 dense 检索失败时返回空 dense 结果并继续 BM25/RRF/校验/rerank。
   - 非 hybrid 模式仍抛出异常，保留故障可见性。
5. `DebugController.tokenUsage` 改为读取 `TokenUsageService#getDailyQuota()`。
6. 新增 `client/src/utils/markdown.js`，在 `marked.parse` 前转义原始 HTML。
7. `App.vue` 和 `ChatBubble.vue` 使用 `renderSafeMarkdown`。
8. 恢复 `client/index.html` 为 Vite 源码入口。
9. 将默认前端 `npm run build` 改为输出 `tmp-build-dist`，并保留 `npm run build:dist` 用于需要传统 `dist` 输出的环境。

## 4. 新增或完善的测试

1. `AgentControllerTest`
   - 覆盖非法 `videoId` 降级为未限定视频范围。
   - 覆盖 SSE 使用注入执行器，测试不再依赖异步 timeout。
2. `RetrievalServiceTest`
   - 覆盖 hybrid dense 检索失败时降级 BM25。
3. `DebugControllerTest`
   - 覆盖 token usage 使用配置化 daily quota。

## 5. 验证命令及结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd server; mvn -q test` | 通过 | 后端全量单元测试通过。输出包含 Maven/JDK/Mockito 警告，不影响退出码。 |
| `cd server; mvn -q -DskipTests package` | 通过 | 后端可打包。输出包含 JDK native access/Unsafe 警告，不影响退出码。 |
| `cd client; npm run build` | 通过 | 默认输出到 `tmp-build-dist`，避开当前 Windows 环境中被占用的旧 `dist`。 |
| `cd client; npm run build:dist` | 失败 | 传统输出目录 `client/dist` 下旧产物被 Windows 占用，报 EPERM。保留该命令用于未占用 `dist` 的环境。 |
| `git diff --check` | 通过 | 未发现 whitespace error 或 conflict marker；输出仅包含 LF/CRLF 提示。 |
| `cd server; mvn -q "-Dtest=AgentControllerTest" test` | 通过 | Agent Controller 回归测试通过。 |
| `cd server; mvn -q "-Dtest=RetrievalServiceTest" test` | 通过 | RAG 检索回归测试通过。 |
| `cd server; mvn -q "-Dtest=DebugControllerTest" test` | 通过 | token quota 接口回归测试通过。 |

## 6. 当前结论

后端已经能够编译并通过现有及新增单元测试。默认前端构建命令已经能够完成并输出到 `tmp-build-dist`；传统 `dist` 输出在当前 Windows 环境中仍受旧文件占用影响。核心 Agent/RAG 链路的高优先级可运行性和安全问题已做小范围修复，外部服务端到端联调仍需在完整依赖启动后执行。
