# Claw助手 流程图

WeChat Bot · iLink 协议 · 和风天气 · DeepSeek AI · 通义万相 · 通义千问 VL

---

## 0. 程序入口 — 控制台循环

```mermaid
flowchart LR
    MAIN["main()<br>YoukedaProjectApplication.java:14"]:::entry
    MAIN --> INIT["Spring Boot 启动<br>SpringApplication.run()"]:::process
    INIT --> CTX["获取 Bean<br>CommandHandler + WeatherService"]:::process
    CTX --> CONSOLE{"Scanner 控制台<br>scanner.hasNextLine()"}:::decision
    CONSOLE --> |"exit"| EXIT["退出程序"]:::entry
    CONSOLE --> |"help / version / status"| CMD["CommandHandler<br>CommandHandler.java:17"]:::entry
    CONSOLE --> |"weather 城市"| W0["WeatherService<br>控制台查询"]:::entry
    CONSOLE --> |"ilink login"| L0["→ iLink 登录"]:::entry
    CONSOLE --> |"ilink status / stop"| S0["→ 状态/断开"]:::entry

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
    QR["getQRCode()<br>GET /get_bot_qrcode?bot_type=3<br>IlinkProtocolClient.java:66"]:::api
    QR_R["← { qrcode, qrcode_img_content }"]:::api
    PRINT["打印二维码 URL"]:::process
    QR --> QR_R --> PRINT
    POLL["每 2 秒轮询<br>pollQRStatus(qrcode)<br>IlinkProtocolClient.java:74"]:::api
    PRINT --> POLL
    STATUS{"status?"}:::decision
    POLL --> STATUS
    STATUS --> |"waiting"| W["等待中"]:::process
    W -.->|"继续轮询"| POLL
    STATUS --> |"expired"| E["二维码过期"]:::process
    STATUS --> |"confirmed"| C["扫码确认"]:::process
    C --> CRED["setCredentials(botToken, baseUrl)<br>保存 token，更新 baseUrl<br>记录 ownerId（ilink_user_id）"]:::process
    CRED --> POLLING["startPolling()<br>启动消息轮询"]:::process

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef api fill:#e8f5e9,stroke:#388e3c;
    classDef decision fill:#fce4ec,stroke:#c62828;
```

请求微信 iLink 服务器获取二维码 → 返回 `qrcode_id` 和二维码 URL → 打印到控制台 → 每 2 秒轮询状态 → 用户扫码确认后返回 `bot_token`（身份凭证）和 `baseUrl`。登录成功后记录 `ownerId`（`ilink_user_id`），用于后续欢迎消息触发。

★ `LoginResult` 包含 `ilink_user_id`（主人微信ID）、`ilink_bot_id`（机器人ID）、`bot_token`、`baseUrl`。

---

## 2. 消息轮询（独立线程）

```mermaid
flowchart LR
    PS["startPolling()<br>new Thread → daemon<br>IlinkService.java:93"]:::entry
    PI["cursor = ''"]:::process
    PS --> PI
    PL{"polling &&<br>connected?"}:::decision
    PI --> PL
    PL --> |"否"| PE["线程结束"]:::process
    PL --> |"是"| GET["getUpdates(cursor)<br>POST /getupdates<br>IlinkProtocolClient.java:82"]:::api
    GET --> LP["长轮询 35 秒<br>有消息立即返回<br>超时空返回"]:::process
    LP --> RESP["← { msgs, get_updates_buf }"]:::api
    RESP --> CU["更新 cursor"]:::process
    CU --> HM{"msgs 不为空?"}:::decision
    HM --> |"否"| PL
    HM --> |"是"| FOR["for 遍历 msgs"]:::process
    FOR --> EXT["extractText(msg)<br>extractFirstImage(msg)<br>取 item_list 内容"]:::process
    EXT --> VAL{"contextToken 不为空<br>且<br>（有文本 或 有图片）?"}:::decision
    VAL --> |"否"| SK["跳过"]:::process
    SK -.->|"继续"| FOR
    VAL --> |"是"| LOG["日志 + 打印<br>→ handleMessage"]:::process
    GET -.-> ERR{"异常?"}:::decision
    ERR --> |"是"| EL["sleep 6s"]:::process
    EL --> PL
    ERR --> |"否"| LP

    classDef entry fill:#e1f5fe,stroke:#0288d1;
    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef api fill:#e8f5e9,stroke:#388e3c;
    classDef decision fill:#fce4ec,stroke:#c62828;
```

长轮询：服务器挂起最多 35 秒，有消息立即返回。每次返回 `get_updates_buf`（游标），下次请求时带回，确保不重复消费。过滤条件：必须有 `contextToken`，且至少包含文本或图片（单纯语音消息跳过）。

---

## 3. 消息路由 — handleMessage

```mermaid
flowchart LR
    H["handleMessage(msg)<br>IlinkService.java:142"]:::entry
    H --> W{"是主人首条消息?<br>!welcomeSent && ownerId==fromUser"}:::decision
    W --> |"是"| WS["发送欢迎语（消耗 contextToken）<br>welcomeSent = true<br>return"]:::process
    W --> |"否"| IMG{"有图片?"}:::decision
    IMG --> |"是"| IR["→ 图片识别<br>downloadAndDecrypt → 通义千问VL"]:::external
    IMG --> |"否"| T["取文本 text"]:::process
    T --> R{"路由判断"}:::decision
    R --> |"weather/天气"| WE["→ 天气查询"]:::external
    R --> |"图片生成"| IG["→ 通义万相生成 → CDN上传 → 图片消息"]:::external
    R --> |"其他"| AI["→ AI对话<br>DeepSeek"]:::external
    R --> |"未来"| FT["未来扩展"]:::future

    classDef entry fill:#e1f5fe,stroke:#0288d1;
    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef external fill:#f3e5f5,stroke:#7b1fa2;
    classDef future fill:#e0e0e0,stroke:#9e9e9e,stroke-dasharray:8 4;
```

路由流程：
1. **首条消息检测**：若为登录记录的主人首次发消息，仅回复欢迎语（消耗该消息），不处理内容
2. **图片消息**：CDN 下载 → AES 解密 → 通义千问 VL 识别
3. **文本消息**：
   - `weather` / `天气` 开头 → 和风天气查询
   - `图片生成` 开头 → 通义万相生图 → CDN 上传 → 图片消息
   - 其他 → DeepSeek AI 对话

路由分支用 `startsWith` 判断，未来可在此处扩展更多命令。

---

## 4. 首条消息欢迎机制

```mermaid
flowchart LR
    LOGIN["登录成功<br>记录 ownerId<br>welcomeSent = false"]:::entry
    MSG["收到主人消息"]:::process
    CHK{"welcomeSent?<br>ownerId==fromUser?"}:::decision
    MSG --> CHK
    CHK --> |"false, 是主人"| SEND["回复欢迎语<br>消耗 contextToken<br>welcomeSent = true"]:::process
    SEND --> RET["return（不处理消息内容）"]:::process
    CHK --> |"true 或非主人"| NORMAL["正常消息路由"]:::process

    classDef entry fill:#e1f5fe,stroke:#0288d1;
    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
```

因 iLink 协议不支持 Bot 主动推送消息，欢迎语通过「消耗主人第一条消息」的方式发送。
首次连接后，主人发的第一条消息会被用于回复欢迎语（消息内容被消耗不处理）。

---

## 5. 天气查询（和风天气）

```mermaid
flowchart LR
    WS["queryWeather(city)<br>WeatherService.java:55"]:::process
    WE{"空?"}:::decision
    WS --> WE
    WE --> |"是"| WEE["返回: 请输入城市名"]:::process
    WE --> |"否"| WM{"CITY_MAP<br>查到 cityId?"}:::decision
    WM --> |"否"| WEC["返回: 暂不支持该城市"]:::process
    WM --> |"是"| WA["GET 和风天气<br>/v7/weather/now<br>?location=cityId&key=xxx<br>自定义域名 na3h2taynf.re.qweatherapi.com"]:::api
    WA --> WR["← JSON<br>{now:{text,temp,feelsLike,humidity,windDir,windScale}}"]:::api
    WR --> WC{"code==200?"}:::decision
    WC --> |"否"| WEA["返回: API异常"]:::process
    WC --> |"是"| WF["格式化天气文本<br>如：北京天气: 晴 25℃"]:::process
    WF --> WI["addExchange()<br>注入 AI 对话历史"]:::process
    WI --> WOK["回复用户"]:::process
    WEE --> WER["reply = 错误文本"]:::process
    WEC --> WER
    WEA --> WER

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

预置 10 城市 ID 映射表（北京/上海/广州/深圳/杭州/成都/武汉/南京/西安/重庆）。通过自定义域名 `na3h2taynf.re.qweatherapi.com` 请求和风天气 API，解析 JSON 后格式化为可读文本。结果注入 AI 对话历史，便于后续上下文追问。

---

## 6. AI 对话（DeepSeek）

```mermaid
flowchart LR
    AS["sessions.computeIfAbsent(userId)<br>ConcurrentHashMap<br>AIService.java:48"]:::process
    AE{"首次对话?"}:::decision
    AS --> AE
    AE --> |"是"| AN["创建 session<br>添加 system 消息"]:::process
    AE --> |"否"| AO["使用现有历史"]:::process
    AN --> AA
    AO --> AA
    AA["追加 user 消息到历史"]:::process
    AT["trimHistory()<br>保留最近 10 轮"]:::process
    AR["POST /chat/completions<br>model: deepseek-chat<br>api.deepseek.com<br>byte[] 编码防乱码"]:::api
    AX["← choices[0].message.content"]:::api
    AA --> AT --> AR --> AX
    AX --> AC{"有内容?"}:::decision
    AC --> |"否"| AER["返回: 无内容"]:::process
    AC --> |"是"| AAS["追加 assistant 回复到历史"]:::process
    AAS --> AOK["reply = AI 回复 → 回复用户"]:::process

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

每个微信用户独立对话 session（`ConcurrentHashMap<String, List<ChatMessage>>`），结构：`[system, user, assistant, user, ...]`。最多保留 10 轮对话，超出截断。使用 `byte[]` 编解码防止 Windows 控制台乱码。

---

## 7. 图片识别（通义千问 VL）

```mermaid
flowchart LR
    IR["handleMessage → 有图片<br>IlinkService.java:154"]:::entry
    IR --> DL["downloadAndDecryptImage()<br>IlinkProtocolClient.java:101"]:::process
    DL --> URL{"media.full_url<br>或 encrypt_query_param?"}:::decision
    URL --> |"full_url"| D1["GET full_url<br>下载加密字节"]:::api
    URL --> |"encrypt_query_param"| D2["拼接 CDN_BASE + /download<br>?encrypted_query_param=..."]:::process
    D2 --> D1
    D1 --> AES{"有 aeskey?"}:::decision
    AES --> |"否"| RAW["返回原始密文"]:::process
    AES --> |"是"| K["deriveAesKey()<br>3种格式兼容解析"]:::process
    K --> DEC["AES-128-ECB 解密<br>去除 PKCS7 padding"]:::process
    DEC --> B64["Base64 编码 + 检测 MIME<br>→ data:image/jpeg;base64,..."]:::process
    B64 --> VL["通义千问 VL<br>POST dashscope 兼容模式<br>model: qwen-vl-plus<br>content: [{text},{image_url}]"]:::api
    VL --> RESP["← choices[0].content<br>图片描述文本"]:::api
    RESP --> SEND["回复用户"]:::process

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

1. **CDN 下载**：从 `image_item.media.full_url` 或拼接 CDN URL 下载 AES 加密的图片字节
2. **AES 解密**：`deriveAesKey()` 兼容 3 种 key 格式（32字符 hex / base64(hex) / base64(raw 16字节)），AES-128-ECB 解密后去除 PKCS7 padding
3. **视觉识别**：图片 Base64 → `data:` URI → 通义千问 VL（`qwen-vl-plus`），复用 DashScope key

---

## 8. 图片生成（通义万相）→ CDN 上传 → 发送

```mermaid
flowchart LR
    IG["handleMessage → 图片生成 xxx<br>IlinkService.java:178"]:::entry
    IG --> GEN["generateImage(prompt)<br>ImageService.java:32"]:::process
    GEN --> API["通义万相<br>POST /multimodal-generation/generation<br>model: wan2.6-t2i<br>DashScope"]:::api
    API --> RESP["← output.choices[0].content[0].image<br>OSS 图片 URL"]:::api
    RESP --> OK{"URL 有效?"}:::decision
    OK --> |"否"| ERR["回复错误文本"]:::process
    OK --> |"是"| DL2["downloadImage(url)<br>从 OSS 下载图片字节"]:::process
    DL2 --> ENC["AES-128-ECB 加密<br>随机生成 16 字节 key"]:::process
    ENC --> MD5["计算 rawfilemd5<br>计算 padded filesize"]:::process
    MD5 --> UPL["getUploadUrl<br>POST /ilink/bot/getuploadurl<br>含 filekey/aeskey/rawsize/rawfilemd5"]:::api
    UPL --> UPL_R["← upload_full_url 或 upload_param"]:::api
    UPL_R --> CDN["POST 加密字节到 CDN URL"]:::process
    CDN --> CDN_R["← x-encrypted-param header"]:::api
    CDN_R --> SEND2["发送 image_item<br>cdencrypt_query_param + aes_key<br>→ sendMessage"]:::process
    SEND2 --> DONE["用户收到图片"]:::process

    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef decision fill:#fce4ec,stroke:#c62828;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

1. **生图**：通义万相 `wan2.6-t2i`（同步模式 POST），返回 OSS 图片 URL
2. **加密**：随机 16 字节 AES key → AES-128-ECB 加密图片字节（PKCS5Padding）
3. **CDN 上传**：请求上传 URL → POST 加密字节 → 获取 `x-encrypted-param`
4. **发送**：构建 `image_item.cdnImage(CDNMedia, midSize)` → `sendMessage`

★ `getUploadUrl` 请求包含：`filekey`（32 hex）、`media_type:1`、`to_user_id`、`rawsize`、`rawfilemd5`、`filesize`（填充后）、`aeskey`（hex）、`no_need_thumb:true`。

---

## 9. 发送回复到微信

```mermaid
flowchart LR
    SR["sendMessage(toUserId, contextToken, items)<br>IlinkProtocolClient.java:88"]:::entry
    SB["构建 SendMessageRequest<br>• client_id = UUID（去重）<br>• context_token = 原值带回<br>• message_type=2, message_state=2<br>• item_list = [{text}] 或 [{image}]"]:::process
    SB --> BASE["base_info: {channel_version: '1.0.2'}"]:::process
    BASE --> SE["byte[] body = UTF-8<br>★ 防 Windows GBK 乱码"]:::process
    SE --> SA["三个认证头<br>① AuthorizationType: ilink_bot_token<br>② Authorization: Bearer {token}<br>③ X-WECHAT-UIN: Base64(随机 int)"]:::process
    SA --> SI["POST /sendmessage<br>RestClient.post().body(byte[])"]:::api
    SI --> SP["← {} 接受成功<br>（不代表已送达用户）"]:::api
    SP --> SL["日志: 已回复 userId"]:::process

    classDef entry fill:#e1f5fe,stroke:#0288d1;
    classDef process fill:#fff3e0,stroke:#f57c00;
    classDef api fill:#e8f5e9,stroke:#388e3c;
```

- `client_id` = UUID（每次不同，去重幂等性）
- `context_token` = 原值带回（关联原消息）
- `X-WECHAT-UIN` = 随机整数 → 无符号十进制 → Base64（防重放）
- 响应 `{}` 仅表示服务器接受，不代表已送达用户微信
- 所有 HTTP 响应使用 `byte[]` 读取再 UTF-8 解码，避免 GBK 乱码

---

## 10. iLink 断开

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
