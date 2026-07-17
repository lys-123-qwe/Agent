# Claw助手 流程图

WeChat Bot · iLink 协议 · 和风天气 · DeepSeek AI

---

## 0. 程序入口 — 控制台循环

```mermaid
flowchart LR
    MAIN["main()<br>YoukedaProjectApplication.java:14"]:::entry
    MAIN --> INIT["Spring Boot 启动<br>SpringApplication.run()"]:::process
    INIT --> CTX["获取 Bean"]:::process
    CTX --> CONSOLE{"Scanner 控制台"}:::decision
    CONSOLE --> |"exit"| EXIT["退出程序"]:::entry
    CONSOLE --> |"help / version / status"| CMD["CommandHandler"]:::entry
    CONSOLE --> |"weather"| W0["WeatherService"]:::entry
    CONSOLE --> |"ilink login"| L0["→ iLink 登录"]:::entry
    CONSOLE --> |"ilink stop"| S0["→ 断开"]:::entry

    classDef entry fill:#e1f5fe,stroke:#0288d1;
    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
```

Spring Boot 启动后进入 `main()`，使用 `Scanner` 监听控制台输入。
支持命令：`help` / `version` / `status` / `weather <城市>` / `ilink login/status/stop` / `exit`。

---

## 1. iLink 登录流程

```mermaid
flowchart LR
    QR["getQRCode()<br>GET /get_bot_qrcode<br>IlinkProtocolClient.java:52"]:::api
    QR_R["← { qrcode, qrcode_img_content }"]:::api
    PRINT["打印二维码 URL"]:::process
    QR --> QR_R --> PRINT
    POLL["每 2 秒轮询<br>pollQRStatus(qrcode)<br>IlinkProtocolClient.java:60"]:::api
    PRINT --> POLL
    STATUS{"status?"}:::decision
    POLL --> STATUS
    STATUS --> |"waiting"| W["等待"]:::process
    W -.->|"循环"| POLL
    STATUS --> |"expired"| E["过期"]:::process
    STATUS --> |"confirmed"| C["扫码确认"]:::process
    C --> CRED["setCredentials()<br>保存 token，更新 baseUrl"]:::process

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef api fill:#e8f5e9,stroke:#388e3c;
    classDef decision fill:#fce4ec,stroke:#c62828;
```

请求 iLink 服务器获取二维码 → 返回 `qrcode_id` 和二维码 URL → 打印到控制台 → 每 2 秒轮询状态 → 用户扫码确认后返回 `bot_token`（身份凭证）和 `baseUrl`。

★ `bot_token` 是后续所有请求的密码。`baseUrl` 可能从 `ilinkai` 变为 `ilinkii`（负载均衡），需动态重建 RestClient。

---

## 2. 消息轮询（独立线程）

```mermaid
flowchart LR
    PS["startPolling()<br>new Thread<br>IlinkService.java:76"]:::entry
    PI["cursor = ''"]:::process
    PS --> PI
    PL{"polling &&<br>connected?"}:::decision
    PI --> PL
    PL --> |"否"| PE["结束"]:::process
    PL --> |"是"| GET["getUpdates(cursor)<br>POST /getupdates<br>IlinkProtocolClient.java:68"]:::api
    GET --> LP["长轮询 35 秒"]:::process
    LP --> RESP["← { msgs, get_updates_buf }"]:::api
    RESP --> CU["更新 cursor"]:::process
    CU --> HM{"有消息?"}:::decision
    HM --> |"否"| PL
    HM --> |"是"| FOR["遍历 msgs"]:::process
    FOR --> EXT["取文本"]:::process
    EXT --> VAL{"有效?"}:::decision
    VAL --> |"否"| SK["跳过"]:::process
    SK -.->|"继续"| FOR
    VAL --> |"是"| LOG["→ handleMessage"]:::process
    GET -.-> ERR{"异常?"}:::decision
    ERR --> |"是"| EL["sleep 6s"]:::process
    EL --> PL
    ERR --> |"否"| LP

    classDef entry fill:#e1f5fe,stroke:#0288d1;
    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef api fill:#e8f5e9,stroke:#388e3c;
    classDef decision fill:#fce4ec,stroke:#c62828;
```

长轮询：服务器挂起最多 35 秒，有消息立即返回。每次返回 `get_updates_buf`（游标），下次请求时带回，确保不重复消费。

---

## 3. 消息路由

```mermaid
flowchart LR
    H["handleMessage(msg)<br>IlinkService.java:118"]:::process
    T["extractText(msg)"]:::process
    L["text.toLowerCase()"]:::process
    H --> T --> L
    D{"以什么开头?"}:::decision
    L --> D
    D --> |"weather/天气"| W["→ 天气查询"]:::external
    D --> |"其他"| A["→ AI 对话"]:::external
    D --> |"..."| F["未来扩展"]:::future

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef external fill:#f3e5f5,stroke:#7b1fa2;
    classDef future fill:#e0e0e0,stroke:#9e9e9e,stroke-dasharray:8 4;
```

从 `item_list` 提取文本 → 判断开头：`weather`/`天气` 走天气查询，其他走 AI。

---

## 4. 天气查询（和风天气）

```mermaid
flowchart LR
    WS["queryWeather(city)<br>WeatherService.java:55"]:::process
    WE{"空?"}:::decision
    WS --> WE
    WE --> |"是"| WEE["请输入城市名"]:::process
    WE --> |"否"| WM{"查到<br>cityId?"}:::decision
    WM --> |"否"| WEC["暂不支持"]:::process
    WM --> |"是"| WA["GET /v7/weather/now<br>?location=cityId&key=xxx"]:::api
    WA --> WR["← JSON<br>{now:{text,temp,...}}"]:::api
    WR --> WC{"code==200?"}:::decision
    WC --> |"否"| WEA["API异常"]:::process
    WC --> |"是"| WF["格式化文本"]:::process
    WF --> WI["addExchange()<br>注入 AI 历史"]:::process
    WI --> WOK["reply = 天气文本"]:::process
    WEE --> WER["reply = 错误"]:::process
    WEC --> WER
    WEA --> WER

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

预置 10 城市 ID → 查和风天气 API → 解析 JSON → 格式化文本。结果注入 AI 历史，使后续"明天呢"等上下文问题得以理解。

---

## 5. DeepSeek AI 对话

```mermaid
flowchart LR
    AS["sessions.computeIfAbsent(userId)<br>AIService.java:42"]:::process
    AE{"首次?"}:::decision
    AS --> AE
    AE --> |"是"| AN["创建 session<br>+ system 消息"]:::process
    AE --> |"否"| AO["现有历史"]:::process
    AN --> AA
    AO --> AA
    AA["追加 user 消息"]:::process
    AT["trimHistory()<br>保留 10 轮"]:::process
    AR["POST /chat/completions<br>deepseek-chat"]:::api
    AX["← choices[0].content"]:::api
    AA --> AT --> AR --> AX
    AX --> AC{"有内容?"}:::decision
    AC --> |"否"| AER["无内容"]:::process
    AC --> |"是"| AAS["追加 assistant"]:::process
    AAS --> AOK["reply = AI 回复"]:::process

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

每用户独立 session（`ConcurrentHashMap`），最多 10 轮历史，超出截断。请求 DeepSeek API 并用 byte[] 防乱码。

---

## 6. 发送回复到微信

```mermaid
flowchart LR
    SR["sendMessage(toUserId,<br>contextToken, text)<br>IlinkProtocolClient.java:74"]:::entry
    SB["构建 JSON<br>• client_id = UUID<br>• context_token 原值带回<br>• item_list = [{text}]"]:::process
    SE["byte[] = UTF-8<br>防 GBK 乱码"]:::process
    SA["三个认证头<br>① AuthType<br>② Bearer token<br>③ X-WECHAT-UIN"]:::process
    SU["X-WECHAT-UIN 生成:<br>randomInt → toString<br>→ Base64.encode"]:::process
    SI["POST /sendmessage"]:::api
    SP["← {} 接受成功"]:::api
    SR --> SB --> SE --> SA --> SU --> SI --> SP

    classDef entry fill:#e1f5fe,stroke:#0288d1;
    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

- `client_id` = UUID（去重）
- `context_token` = 原值带回（关联原消息）
- `X-WECHAT-UIN` = 随机数 Base64（防重放）
- 响应 `{}` 表示已接受，不代表已送达

---

## 7. iLink 断开

```mermaid
flowchart LR
    SP["polling.set(false)"]:::process
    SC["connected.set(false)"]:::process
    STH["pollThread.interrupt()"]:::process
    SCL["清除 bot_token"]:::process
    SP --> SC
    SC --> SCL
    SP --> STH

    classDef process fill:#fff3e0,stroke:#f57c00;
```

轮询条件不满足 → 线程自然退出。线程为 daemon 模式，程序退出时自动结束。
