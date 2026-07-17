# Claw助手 流程图

```mermaid
flowchart TB
    classDef entry fill:#e1f5fe,stroke:#0288d1,stroke-width:2px
    classDef process fill:#fff3e0,stroke:#f57c00,stroke-width:1px
    classDef api fill:#e8f5e9,stroke:#388e3c,stroke-width:1px
    classDef decision fill:#fce4ec,stroke:#c62828,stroke-width:1px
    classDef external fill:#f3e5f5,stroke:#7b1fa2,stroke-width:1px
    classDef future fill:#e0e0e0,stroke:#9e9e9e,stroke-width:1px,stroke-dasharray:8 4

    MAIN["程序入口 main()<br>YoukedaProjectApplication.java"]:::entry
    MAIN --> CONSOLE{"输入命令"}:::decision
    CONSOLE --> |"exit"| EXIT["退出程序"]:::entry
    CONSOLE --> |"help / version / status"| CMD["CommandHandler<br>CommandHandler.java"]:::entry
    CONSOLE --> |"weather"| WEATHER_CMD["WeatherService<br>控制台查询"]:::entry
    CONSOLE --> |"ilink login"| ILINK_START["IlinkService.start()"]:::entry
    CONSOLE --> |"ilink stop"| ILINK_STOP1["IlinkService.stop()"]:::entry

    subgraph LOGIN["① iLink 登录流程"]
        QR["getQRCode()<br>GET /get_bot_qrcode"]:::api
        QR_RESP["返回 { qrcode, qrcode_img_content }"]:::api
        PRINT["打印二维码 URL 到控制台"]:::process
        POLL_API["pollQRStatus(qrcode)<br>每 2 秒 GET /get_qrcode_status"]:::api
        STATUS{"status 字段?"}:::decision
        EXPIRED["二维码过期 → 结束"]:::process
        WAITING["等待中..."]:::process
        CONFIRMED["扫码确认成功!"]:::process
        CRED["setCredentials()<br>保存 bot_token<br>更新 baseUrl"]:::process
        QR --> QR_RESP --> PRINT
        PRINT --> POLL_API --> STATUS
        STATUS --> |"waiting"| WAITING --> POLL_API
        STATUS --> |"expired"| EXPIRED
        STATUS --> |"confirmed"| CONFIRMED --> CRED
    end

    subgraph POLLING["② 消息轮询（独立线程）"]
        POLL_START["startPolling()<br>new Thread → daemon"]:::entry
        UPDATES["getUpdates(cursor)<br>POST /getupdates<br>长轮询 35 秒超时"]:::api
        UPDATES_RESP["返回 { msgs, get_updates_buf }"]:::api
        HAS_MSG{"msgs 不为空?"}:::decision
        FOR_MSG["遍历 msgs<br>提取文本 + contextToken"]:::process
        ERR{"抛异常?"}:::decision
        ERR_HANDLE["日志警告<br>sleep 6s 继续"]:::process
        POLL_CHECK{"polling 仍为 true?"}:::decision
        POLL_START --> UPDATES
        UPDATES --> ERR
        ERR --> |"是"| ERR_HANDLE --> POLL_CHECK
        ERR --> |"否"| UPDATES_RESP --> HAS_MSG
        HAS_MSG --> |"否"| POLL_CHECK
        HAS_MSG --> |"是"| FOR_MSG
        POLL_CHECK --> |"是"| UPDATES
    end

    subgraph ROUTE["③ 消息路由"]
        HANDLE["handleMessage(msg)"]:::process
        EXTRACT["extractText(msg)<br>从 item_list 取文本"]:::process
        DECIDE{"text 以什么开头?"}:::decision
        W_PATH["weather / 天气"]:::external
        AI_PATH["其他消息"]:::external
        MORE["... 后续可扩展"]:::future
        HANDLE --> EXTRACT --> DECIDE
        DECIDE --> W_PATH
        DECIDE --> AI_PATH
        DECIDE --> MORE
    end

    subgraph WEATHER["④ 天气查询"]
        W_CITY["取城市名"]:::process
        W_MAP{"CITY_MAP查到 cityId?"}:::decision
        W_API_REQ["和风天气 API<br>GET /v7/weather/now"]:::api
        W_PARSE["解析 JSON<br>温度 / 天气 / 湿度"]:::process
        W_INJECT["addExchange()<br>注入 AI 历史"]:::process
        W_ERR["暂不支持该城市"]:::process
        W_CITY --> W_MAP
        W_MAP --> |"是"| W_API_REQ --> W_PARSE --> W_INJECT
        W_MAP --> |"否"| W_ERR
    end

    subgraph AI["⑤ DeepSeek AI 对话"]
        AI_SESSION["获取/创建用户 session<br>ConcurrentHashMap"]:::process
        AI_ADD["追加 user 消息"]:::process
        AI_TRIM["trimHistory()<br>保留最近 10 轮"]:::process
        AI_REQ["POST /chat/completions<br>deepseek-chat"]:::api
        AI_RESP["解析 choices[0]<br>追加 assistant"]:::process
        AI_SESSION --> AI_ADD --> AI_TRIM --> AI_REQ --> AI_RESP
    end

    subgraph SEND["⑥ 发送回复到微信"]
        SEND_REQ["sendMessage(toUserId, contextToken, text)"]:::entry
        BUILD["构建 JSON<br>{ client_id: UUID,<br>  context_token: 原值,<br>  item_list: [{text}] }"]:::process
        AUTH["加三个认证头<br>AuthType + Bearer + X-WECHAT-UIN"]:::process
        ENCODE["body = byte[] UTF-8<br>防 Windows 乱码"]:::process
        SEND_API["POST /sendmessage"]:::api
        SEND_RESP["响应 {} 即接受"]:::api
        SEND_REQ --> BUILD --> AUTH --> ENCODE --> SEND_API --> SEND_RESP
    end

    ILINK_START --> QR
    CRED --> POLL_START
    FOR_MSG --> HANDLE
    W_PATH --> W_CITY
    AI_PATH --> AI_SESSION
    W_INJECT --> SEND_REQ
    W_ERR --> SEND_REQ
    AI_RESP --> SEND_REQ
    SEND_RESP --> POLL_CHECK
```

## 项目文件结构

### 入口层
```
YoukedaProjectApplication.java     ─  main() 入口 + 控制台循环
```

### 协议层（iLink DTO，共 7 个文件）
```
protocol/
 ├── QRCodeResponse.java            ─  get_bot_qrcode 响应
 ├── LoginResult.java               ─  get_qrcode_status 响应（含 bot_token）
 ├── GetUpdatesResponse.java        ─  getupdates 响应（含消息列表 + 游标）
 ├── WeixinMessage.java             ─  单条微信消息结构
 ├── MessageItem.java               ─  消息条目（可嵌套文本/图片等）
 ├── TextItem.java                  ─  文本内容
 └── SendMessageRequest.java        ─  sendmessage 请求体
```

### 客户端层
```
client/
 └── IlinkProtocolClient.java       ─  iLink HTTP 客户端（getQRCode / pollQRStatus /
                                       getUpdates / sendMessage + 认证头生成）
```

### 服务编排层
```
service/
 ├── IlinkService.java              ─  登录流程 + 消息轮询线程 + 消息路由分发
 └── WeatherService.java            ─  和风天气 API 查询（预置 10 城市）
```

### AI 层（DeepSeek DTO，共 5 个文件）
```
ai/
 ├── AIService.java                 ─  DeepSeek API 对话（每用户独立 session）
 ├── ChatRequest.java               ─  请求体
 ├── ChatResponse.java              ─  响应体
 ├── ChatMessage.java               ─  消息体（role + content）
 └── ChatChoice.java                ─  选择结果
```

### 配置层
```
config/
 └── ApiConfig.java                 ─  读取 application.properties（weather + ai）
```

### 命令层
```
command/
 └── CommandHandler.java            ─  控制台命令分发（help/version/status/ilink）
```
