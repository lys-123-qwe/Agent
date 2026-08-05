# 前后端分离设计文档（Vue3 聊天前端 + Spring Boot 后端）

> 目标：前端独立 Vue3 工程，通过 SSE 与后端聊天；后端暴露 1 个流式聊天接口，复用现有 AI Function Calling 能力。微信 bot 不受影响。

## 1. 总体架构

```
浏览器（Vue3 前端，:5173）
   │  SSE  GET /api/chat/stream?msg=xxx
   ▼
Spring Boot 后端（:8080）
   ├── controller/ChatController   （新增，SSE 端点）
   │     └── AIService.chatStream() （新增，流式）
   │           └── DeepSeek 接口 stream=true
   └── 现有工具/服务（AI 通过 Function Calling 自动调用）
```

- **前端**：独立 Vue3 + Vite 工程，只管聊天界面
- **后端**：暴露聊天 API，内部复用现有 AI 能力（工具调用、RAG、记忆）

## 2. 后端设计

### 2.1 新增文件
| 文件 | 作用 |
|---|---|
| `controller/ChatController.java` | SSE 聊天端点 |
| `config/CorsConfig.java` | 允许前端跨域 |
| `AIService.chatStream()` 方法 | 流式调用 DeepSeek |

### 2.2 SSE 聊天接口
```
GET /api/chat/stream?msg=你好
```
响应（text/event-stream）：
```
data: {"delta":"你"}

data: {"delta":"好"}

data: {"delta":"！"}

data: [DONE]
```
- 用 Spring `SseEmitter`，逐段推送
- 每段是一个 `{delta: "..."}` JSON，前端拼起来即打字机效果
- `[DONE]` 表示结束

### 2.3 复用现有能力
- 现有 `AIService.chat()` 已支持：Function Calling 多轮循环、RAG 增强、用户记忆、工具结果收集
- `chatStream()` 在 `chat()` 基础上改造：**多轮工具调用逻辑不变**，只在最终生成回答时改为流式吐出
- 前端发的消息走同一套 AI 流程（能查股票、分析、画图……只是靠打字）

### 2.4 跨域（CORS）
- 开发期前端跑 5173、后端 8080，跨域必须放行
- `CorsConfig` 允许 `http://localhost:5173`，`GET/POST/OPTIONS`

### 2.5 微信不受影响
- 微信消息仍走 `CliRunner` → 现有 `chat()`（整条返回）
- SSE 只服务 Web 前端，两套入口共用底层 AI 逻辑

## 3. 前端设计（Vue3）

### 3.1 工程
```
chat-frontend/
├── index.html
├── vite.config.js        # 代理 /api 到 localhost:8080（开发期免 CORS）
├── src/
│   ├── main.js
│   ├── App.vue           # 整体布局（侧边栏 + 聊天区）
│   ├── api/stream.js     # SSE 接收封装（fetch + ReadableStream）
│   └── components/
│       ├── ChatWindow.vue   # 消息列表
│       ├── MessageBubble.vue # 单条消息气泡（用户/AI）
│       └── InputBar.vue     # 输入框 + 发送 + 停止
```

### 3.2 核心组件
| 组件 | 职责 |
|---|---|
| `ChatWindow` | 渲染消息列表，滚动到底部 |
| `MessageBubble` | 区分用户消息/AI 消息的样式 |
| `InputBar` | 输入、发送、禁用中状态、停止生成 |
| `stream.js` | `fetch` + `ReadableStream` 逐段读取 SSE，实时更新 AI 消息 |

### 3.3 流式接收
- 用 `fetch` + `ReadableStream`（比 EventSource 更灵活，能带参数/控制）
- 收到 `{delta}` → 追加到当前 AI 消息文本
- 收到 `[DONE]` → 结束，恢复输入框

## 4. 数据流

```
用户输入 "分析茅台"
  → 前端 POST/GET SSE
  → 后端 ChatController → AIService.chatStream
  → AI 判定要调 analyze_stock（Function Calling）
  → 后端调工具拿数据 → 结果给 AI
  → AI 生成回答，流式逐段推回
  → 前端打字机显示完整回答
```

## 5. 部署方案

| 环境 | 前端 | 后端 | 说明 |
|---|---|---|---|
| 开发 | Vite dev (5173) | Spring Boot (8080) | vite 代理 /api，无跨域问题 |
| 生产 | `npm run build` → 静态文件 | jar | Nginx 托管前端 + 反代 `/api` 到后端 |

```
生产 Nginx 配置要点：
  location / { proxy_pass 前端静态文件 }
  location /api/ { proxy_pass http://后端:8080; proxy_buffering off; }  # 流式要关缓冲
```

## 6. 里程碑（按序交付）

| 阶段 | 内容 | 产出 |
|---|---|---|
| M1 | 后端：CorsConfig + ChatController + chatStream，curl 验证流式 | SSE 接口可 curl 出流 |
| M2 | 前端：Vue3 工程骨架 + 输入框 + 消息列表 | 能发消息收到回复 |
| M3 | 前端：流式打字机效果 + 停止按钮 + 消息历史 | 完整聊天体验 |
| M4 | 生产部署：Nginx 配置 + 联调 | 前后端分离可上线 |

## 7. 工作量估计

| 部分 | 工作量 |
|---|---|
| 后端 SSE 接口（M1） | 约半天 |
| 前端骨架 + 聊天（M2-M3） | 约 1~2 天 |
| 部署配置（M4） | 约半天 |

## 8. 风险与注意

1. **AI 调用慢**：DeepSeek 生成可能要几秒~十几秒，SSE 首 token 延迟取决于模型，前端要有"思考中"状态
2. **工具调用轮次**：AI 可能先调工具（如查行情）再回答，中途会停顿——前端要有缓冲提示
3. **超时/断线**：SSE 连接可能中断，前端要能重连/提示
4. **并发限制**：每个 SSE 连接占用后端线程/资源，多人同时用要注意线程池上限
