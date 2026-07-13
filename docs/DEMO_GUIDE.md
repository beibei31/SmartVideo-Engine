# SmartVideo Engine 本地演示指南

本文档面向秋招演示和本地验收，记录当前代码能够支撑的真实流程、依赖和请求示例。

## 1. 演示链路

1. 用户登录或注册后上传本地视频，前端调用 `POST /media/upload`；大文件走 `POST /media/chunk/check`、`POST /media/chunk/upload`、`POST /media/chunk/merge`。
2. 后端将视频写入 MinIO，并在 MySQL `media_file` 记录中保存文件名、路径、状态、用户 ID 和上传时间。
3. 前端工作台轮询 `GET /media/list?userId={id}`，卡片显示四段状态：上传、ASR、摘要、RAG。
4. 用户点击“提取文字”，前端调用 `GET /debug/transcribe?id={mediaId}`，后端异步执行音频提取和 ASR，结果写回 `transcriptText`。
5. 用户点击“AI 智能总结”，前端调用 `GET /debug/ai?id={mediaId}`，后端通过 Redis 限流和分布式锁投递 RocketMQ 任务，消费者生成 `aiSummary`。
6. 用户点击“问这个视频”，前端调用 `POST /api/rag/ingest/{mediaId}`，后端把 ASR 文本切块并写入 BM25/Milvus 知识库。
7. Agent Chat 调用 `POST /api/agent/chat/stream`，后端按 `agent_status -> tool_call -> tool_result -> contexts -> final_answer -> done` 发送 SSE。
8. 前端聊天面板展示规划、工具、检索、生成状态，并在最终回答下方附带命中片段、时间戳、来源、chunkId 和分数。

## 2. 启动依赖

基础服务：

```powershell
docker compose up -d
```

`docker-compose.yml` 当前包含 MySQL、Redis、MinIO、RocketMQ、RocketMQ Dashboard、etcd、Milvus。默认端口：

- MySQL: `localhost:3307`
- Redis: `localhost:6379`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`
- RocketMQ NameServer: `127.0.0.1:9876`
- Milvus: `localhost:19530`

后端配置在 `server/src/main/resources/application.properties`。常用环境变量：

- `DEEPSEEK_API_KEY`: 摘要、Agent 规划和回答所需的大模型密钥。
- `ALIYUN_API_KEY`: ASR 语音识别所需密钥。
- `OLLAMA_BASE_URL`、`OLLAMA_EMBEDDING_MODEL`: 本地 embedding 服务，默认 `http://localhost:11434` 和 `bge-large`。
- `FFMPEG_DIR`: ffmpeg 所在目录；如已在 PATH 中可留空。
- `YTDLP_PATH`: 链接上传依赖的 yt-dlp 路径。

启动后端：

```powershell
cd server
mvn spring-boot:run
```

启动前端：

```powershell
cd client
npm run dev
```

前端生产构建：

```powershell
cd client
npm run build
```

## 3. 样例请求

注册：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:9090/user/register" `
  -ContentType "application/json" `
  -Body '{"username":"demo","password":"demo123","nickname":"Demo"}'
```

本地上传：

```powershell
curl.exe -F "file=@D:\videos\demo.mp4" -F "userId=1" http://localhost:9090/media/upload
```

查询列表：

```powershell
Invoke-RestMethod "http://localhost:9090/media/list?userId=1"
```

提交 ASR：

```powershell
Invoke-RestMethod "http://localhost:9090/debug/transcribe?id=1"
```

提交摘要任务：

```powershell
Invoke-RestMethod "http://localhost:9090/debug/ai?id=1"
```

构建视频 RAG 索引：

```powershell
Invoke-RestMethod -Method Post "http://localhost:9090/api/rag/ingest/1"
```

Agent 同步问答：

```powershell
Invoke-RestMethod -Method Post "http://localhost:9090/api/agent/chat" `
  -ContentType "application/json" `
  -Body '{"sessionId":"demo-session","videoId":1,"question":"这段视频讲了哪些面试重点？"}'
```

Agent SSE 问答：

```powershell
curl.exe -N -H "Content-Type: application/json" `
  -d "{\"sessionId\":\"demo-session\",\"videoId\":1,\"question\":\"请给出命中片段和时间点\"}" `
  http://localhost:9090/api/agent/chat/stream
```

## 4. 前端演示重点

- 上传区显示 MD5 校验、分片上传和合并进度。
- 工作台卡片显示 `上传 / ASR / 摘要 / RAG` 四段状态。
- ASR 和摘要任务处于轮询中时，卡片阶段显示加载态，侧边栏展示空/加载/失败文案。
- RAG 构建失败时卡片显示失败态，再次点击“问这个视频”可重试。
- Agent Chat 面板顶部显示当前视频名和 SSE 状态。
- 每条 Agent 回复展示执行过程；最终回答下方展示参考来源，包含片段内容、分数、时间范围、chunkId 和检索类型。

## 5. 已知限制

- 没有 `DEEPSEEK_API_KEY` 时，摘要、Agent 规划和自然语言回答无法真实调用外部大模型。
- 没有 `ALIYUN_API_KEY` 时，ASR 只能通过单元测试或替身验证本地逻辑，不能完成真实语音转写。
- Milvus 依赖 embedding 维度与 `milvus.dimension` 一致；默认 `1024`，需要与本地 embedding 模型匹配。
- `/debug/ai` 依赖 RocketMQ 消费者；如果 MQ 未启动，可用 `/debug/ai-direct?id={mediaId}` 做本地直接分析演示。
- 链接上传依赖 `yt-dlp` 和网络可访问性，离线环境建议使用本地文件上传。
