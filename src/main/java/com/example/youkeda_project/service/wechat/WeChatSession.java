package com.example.youkeda_project.service.wechat;

import com.example.youkeda_project.client.IlinkProtocolClient;
import com.example.youkeda_project.config.SessionConfig;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.QRCodeResponse;
import com.example.youkeda_project.protocol.WeixinMessage;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 封装单个微信 Bot 会话——独立的 IlinkProtocolClient、线程池、
 * 二维码、登录状态和消息监听器。
 *
 * <p>每个会话完全隔离：各自的 HTTP 客户端、轮询线程
 * 和 contextToken 池。多个会话互不干扰。</p>
 */
public class WeChatSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WeChatSession.class);

    /** 会话生命周期状态。 */
    public enum Status {
        /** 已获取二维码，等待用户扫码。 */
        WAITING_QR,
        /** 用户已扫码，登录成功。 */
        LOGGED_IN,
        /** 二维码在扫码前已过期。 */
        EXPIRED,
        /** 登录或运行时错误。 */
        ERROR,
        /** 会话被显式关闭。 */
        CLOSED
    }

    private final String sessionId;
    private final String label;
    private final IlinkProtocolClient client;
    private final ExecutorService listenerPool;    // 单线程轮询 getUpdates 消息
    private final ExecutorService dispatchPool;    // 线程池异步处理消息
    private final SessionConfig sessionConfig;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final LocalDateTime createdAt;
    /** 用户id → 最近一次 contextToken,供定时任务主动发消息使用 */
    private final java.util.concurrent.ConcurrentHashMap<String, String> userTokens = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile Status status = Status.WAITING_QR;
    private volatile String qrContent;
    private volatile String qrcodeId;
    private volatile CompletableFuture<LoginResult> loginFuture;
    private volatile String botToken;
    private volatile String baseUrl;
    private volatile String botId;
    private volatile String userId;
    private volatile String errorMessage;
    private volatile LocalDateTime lastActiveAt;
    private volatile String cursor = "";

    /** 获取用户最近一次消息的 contextToken(定时任务主动发消息用),无则 null */
    public String getUserToken(String userId) {
        return userId == null ? null : userTokens.get(userId);
    }

    // ---- 处理器接口 ----

    @FunctionalInterface
    public interface MessageHandler {
        /** @return 回复文本，或 null 表示不回复 */
        String onMessage(String userId, String text, String contextToken);
    }

    @FunctionalInterface
    public interface ImageMessageHandler {
        /** @return 回复文本，或 null 表示不回复 */
        String onImageMessage(String userId, String prompt, byte[] imageBytes, String contextToken);
    }

    @FunctionalInterface
    public interface VoiceMessageHandler {
        /** @return 回复文本，或 null 表示不回复 */
        String onVoiceMessage(String userId, byte[] audioBytes, String contextToken);
    }

    @FunctionalInterface
    public interface FileMessageHandler {
        /** @return 回复文本，或 null 表示不回复 */
        String onFileMessage(String userId, String fileName, byte[] fileBytes, String prompt, String contextToken);
    }

    // ---- 工厂方法 ----

    /**
     * 创建带独立 IlinkProtocolClient 的新会话。
     */
    /** 创建会话（自动生成 sessionId，默认标签）。 */
    public static WeChatSession create(String label, SessionConfig config, ObjectMapper objectMapper) {
        return create(UUID.randomUUID().toString(), label, config, objectMapper, null, null, null, null);
    }

    /**
     * 创建会话，可选从持久化状态恢复。
     */
    public static WeChatSession create(String sessionId, String label,
                                        SessionConfig config, ObjectMapper objectMapper,
                                        String botToken, String baseUrl, String cursor) {
        return create(sessionId, label, config, objectMapper, botToken, baseUrl, cursor, null);
    }

    public static WeChatSession create(String sessionId, String label,
                                        SessionConfig config, ObjectMapper objectMapper,
                                        String botToken, String baseUrl, String cursor, String botId) {
        IlinkProtocolClient client = new IlinkProtocolClient(objectMapper);

        // 如果是恢复会话，立即设置登录凭证
        if (botToken != null && !botToken.isBlank()) {
            client.setCredentials(botToken, baseUrl);
        }

        ExecutorService listenerPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wechat-session-" + sessionId.substring(0, 8));
            t.setDaemon(true);
            return t;
        });

        ExecutorService dispatchPool = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "wechat-dispatch-" + sessionId.substring(0, 8));
                    t.setDaemon(true);
                    return t;
                });

        return new WeChatSession(sessionId, label, client, listenerPool, dispatchPool,
                config, botToken, baseUrl, cursor, botId);
    }

    private WeChatSession(String sessionId, String label, IlinkProtocolClient client,
                          ExecutorService listenerPool, ExecutorService dispatchPool,
                          SessionConfig sessionConfig,
                          String botToken, String baseUrl, String cursor, String botId) {
        this.sessionId = sessionId;
        this.label = label;
        this.client = client;
        this.listenerPool = listenerPool;
        this.dispatchPool = dispatchPool;
        this.sessionConfig = sessionConfig;
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = this.createdAt;

        if (botToken != null && !botToken.isBlank()) {
            this.botToken = botToken;
            this.baseUrl = baseUrl;
            this.botId = botId;
            this.status = Status.LOGGED_IN;
            this.cursor = cursor != null ? cursor : "";
            log.info("Session {} ({}) restored from persisted state", sessionId, label);
        }
    }

    // ---- 登录流程 ----

    /**
     * 开始登录：获取二维码并异步轮询扫码状态。
     *
     * @return 二维码内容（展示给用户扫码的 URL）
     */
    /** 开始扫码登录：获取二维码、后台轮询扫码状态，返回二维码内容。 */
    public String startLogin() {
        if (status == Status.LOGGED_IN) {
            throw new IllegalStateException("Session " + sessionId + " already logged in");
        }
        if (status == Status.CLOSED) {
            throw new IllegalStateException("Session " + sessionId + " is closed");
        }

        log.info("Session {} ({}) requesting QR code", sessionId, label);
        try {
            QRCodeResponse qrResp = client.getQRCode();
            this.qrcodeId = qrResp.qrcode();
            this.qrContent = qrResp.qrcodeImgContent();
            this.status = Status.WAITING_QR;
            this.loginFuture = new CompletableFuture<>();
            touch();

            // 开始异步轮询扫码状态
            Thread pollThread = new Thread(() -> pollLoginStatus(),
                "qr-poll-" + sessionId.substring(0, 8));
            pollThread.setDaemon(true);
            pollThread.start();

            log.info("Session {} ({}) QR ready: {}", sessionId, label, qrcodeId);
            return qrContent;
        } catch (Exception e) {
            this.status = Status.ERROR;
            this.errorMessage = e.getMessage();
            log.error("Session {} ({}) startLogin failed: {}", sessionId, label, e.getMessage());
            throw new RuntimeException("Failed to start login for session " + label
                    + ": " + e.getMessage(), e);
        }
    }

    private void pollLoginStatus() {
        try {
            while (status == Status.WAITING_QR) {
                Thread.sleep(2000);
                try {
                    LoginResult result = client.pollQRStatus(qrcodeId);
                    log.debug("Session {} QR status: {}", sessionId, result.status());

                    switch (result.status()) {
                        case "confirmed":
                            client.setCredentials(result.botToken(), result.baseurl());
                            this.botToken = result.botToken();
                            this.baseUrl = result.baseurl();
                            this.botId = result.botId();
                            this.userId = result.userId();
                            this.status = Status.LOGGED_IN;
                            touch();
                            loginFuture.complete(result);
                            log.info("Session {} ({}) logged in, botId={}", sessionId, label, botId);
                            return;
                        case "expired":
                            this.status = Status.EXPIRED;
                            this.errorMessage = "QR code expired";
                            loginFuture.completeExceptionally(
                                    new RuntimeException("QR code expired"));
                            log.warn("Session {} ({}) QR expired", sessionId, label);
                            return;
                        case "waiting":
                        case "scanned":
                            // 继续轮询
                            break;
                        default:
                            log.warn("Session {} unexpected QR status: {}", sessionId, result.status());
                            break;
                    }
                } catch (Exception e) {
                    log.warn("Session {} QR poll error: {}", sessionId, e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 阻塞直到登录完成（用户扫码）。
     *
     * @param timeoutMs 最大等待毫秒数
     * @return 登录成功返回 LoginResult
     * @throws Exception 超时或出错时抛出
     */
    /** 阻塞等待扫码登录结果，超时抛超时异常。 */
    public LoginResult waitForLogin(long timeoutMs) throws Exception {
        if (loginFuture == null) {
            throw new IllegalStateException("No login in progress for session " + sessionId);
        }
        try {
            LoginResult result = loginFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            touch();
            return result;
        } catch (Exception e) {
            if (status == Status.WAITING_QR) {
                this.status = Status.EXPIRED;
            }
            throw e;
        }
    }

    // ---- 消息监听 ----

    /**
     * 在该会话的专用线程中长轮询微信消息。
     */
    /** 启动消息监听：单线程长轮询收消息，线程池分发到各类型 handler 处理。 */
    public synchronized void startListening(MessageHandler textHandler,
                                            ImageMessageHandler imageHandler,
                                            VoiceMessageHandler voiceHandler,
                                            FileMessageHandler fileHandler) {
        if (!listening.compareAndSet(false, true)) {
            log.warn("Session {} ({}) listener already running", sessionId, label);
            return;
        }
        if (status != Status.LOGGED_IN) {
            listening.set(false);
            throw new IllegalStateException(
                    "Session " + sessionId + " not logged in, status=" + status);
        }

        listenerPool.submit(() -> {
            log.info("Session {} ({}) listener started", sessionId, label);
            int emptyStreak = 0;
            while (listening.get()) {
                try {
                    GetUpdatesResponse resp = client.getUpdates(cursor);
                    touch();

                    // 更新游标供下次轮询
                    if (resp.getUpdatesBuf() != null && !resp.getUpdatesBuf().isBlank()) {
                        this.cursor = resp.getUpdatesBuf();
                    }

                    List<WeixinMessage> messages = resp.msgs();
                    if (messages == null || messages.isEmpty()) {
                        emptyStreak++;
                        if (emptyStreak % 30 == 0) {
                            log.debug("Session {} ({}) {} empty polls",
                                    sessionId, label, emptyStreak);
                        }
                    } else {
                        emptyStreak = 0;
                        log.info("Session {} ({}) received {} messages",
                                sessionId, label, messages.size());
                        dispatchMessages(messages, textHandler, imageHandler,
                                voiceHandler, fileHandler);
                    }
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // 会话在服务端已过期
                    if (isSessionExpired(e)) {
                        log.warn("Session {} ({}) expired on server - stopping listener",
                                sessionId, label);
                        this.status = Status.EXPIRED;
                        this.errorMessage = "session expired on server";
                        break;
                    }
                    log.error("Session {} ({}) listener error: {}",
                            sessionId, label, e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Session {} ({}) listener stopped", sessionId, label);
        });
    }

    /**
     * 在线程池上把消息分发到各处理器。
     */
    private void dispatchMessages(List<WeixinMessage> messages,
                                  MessageHandler textHandler,
                                  ImageMessageHandler imageHandler,
                                  VoiceMessageHandler voiceHandler,
                                  FileMessageHandler fileHandler) {
        for (WeixinMessage msg : messages) {
            dispatchPool.submit(() -> {
                String fromUser = msg.fromUserId();
                String contextToken = msg.contextToken();
                // 缓存用户 contextToken,供定时任务主动发送消息使用
                if (fromUser != null && contextToken != null && !contextToken.isBlank()) {
                    userTokens.put(fromUser, contextToken);
                }
                if (msg.itemList() == null) return;

                String textPrompt = null;
                byte[] imageBytes = null;
                byte[] voiceBytes = null;
                byte[] fileBytes = null;
                String fileName = null;

                for (MessageItem item : msg.itemList()) {
                    // 提取文本
                    if (item.textItem() != null && item.textItem().text() != null) {
                        textPrompt = item.textItem().text();
                    }
                    // 提取图片
                    if (imageBytes == null && item.imageItem() != null) {
                        try {
                            imageBytes = client.downloadAndDecryptImage(item);
                        } catch (Exception e) {
                            log.error("Session {} download image failed: {}",
                                    sessionId, e.getMessage());
                        }
                    }
                    // 提取语音
                    if (voiceBytes == null && item.voiceItem() != null) {
                        try {
                            voiceBytes = client.downloadVoice(item.voiceItem());
                        } catch (Exception e) {
                            log.error("Session {} download voice failed: {}",
                                    sessionId, e.getMessage());
                        }
                    }
                    // 提取文件
                    if (fileBytes == null && item.fileItem() != null) {
                        var fi = item.fileItem();
                        fileName = fi.fileName();
                        try {
                            fileBytes = client.downloadAndDecryptFile(fi);
                        } catch (Exception e) {
                            log.error("Session {} download file failed: {}",
                                    sessionId, e.getMessage());
                        }
                    }
                }

                try {
                    String reply = null;
                    if (imageBytes != null && imageHandler != null) {
                        String prompt = textPrompt != null ? textPrompt : "请描述这张图片";
                        reply = imageHandler.onImageMessage(fromUser, prompt, imageBytes, contextToken);
                    } else if (voiceBytes != null && voiceHandler != null) {
                        reply = voiceHandler.onVoiceMessage(fromUser, voiceBytes, contextToken);
                    } else if (fileBytes != null && fileHandler != null) {
                        String prompt = textPrompt != null ? textPrompt : "";
                        reply = fileHandler.onFileMessage(fromUser, fileName, fileBytes, prompt, contextToken);
                    } else if (textPrompt != null && textHandler != null) {
                        reply = textHandler.onMessage(fromUser, textPrompt, contextToken);
                    }

                    if (reply != null && !reply.isBlank()) {
                        client.sendMessage(fromUser, contextToken, List.of(MessageItem.text(reply)));
                    }
                } catch (Exception e) {
                    log.error("Session {} message dispatch error: {}",
                            sessionId, e.getMessage(), e);
                }
            });
        }
    }

    /** 停止消息监听。 */
    public void stopListening() {
        if (listening.compareAndSet(true, false)) {
            log.info("Session {} ({}) stopping listener", sessionId, label);
        }
    }

    // ---- 发送方法 ----

    /** 发送文本消息。 */
    public void sendText(String toUserId, String contextToken, String text) throws IOException {
        touch();
        client.sendMessage(toUserId, contextToken, List.of(MessageItem.text(text)));
    }

    /** 发送图片消息（加密上传 CDN 后发送）。 */
    public void sendImage(String toUserId, String contextToken, byte[] imageBytes) {
        touch();
        client.uploadAndSendImage(toUserId, contextToken, imageBytes);
    }

    /** 发送文件消息（加密上传 CDN 后发送）。 */
    public void sendFile(String toUserId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        touch();
        client.uploadAndSendFile(toUserId, contextToken, fileBytes, fileName, fileSize);
    }

    // ---- 持久化 ----

    /** 导出会话状态供持久化。 */
    public SessionState exportState() {
        return new SessionState(sessionId, label, botToken, baseUrl, botId, userId, cursor, status.name());
    }

    // ---- 访问器 ----

    public String getSessionId() { return sessionId; }
    public String getLabel() { return label; }
    public Status getStatus() { return status; }
    public String getQrContent() { return qrContent; }
    public IlinkProtocolClient getClient() { return client; }
    public String getBotId() { return botId; }
    public String getBotToken() { return botToken; }
    public String getBaseUrl() { return baseUrl; }
    public String getUserId() { return userId; }
    public String getErrorMessage() { return errorMessage; }
    public String getCursor() { return cursor; }
    public boolean isLoggedIn() { return status == Status.LOGGED_IN && botToken != null; }
    public boolean isListening() { return listening.get(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }

    private void touch() {
        this.lastActiveAt = LocalDateTime.now();
    }

    /** 判断异常是否表示服务端会话已过期。 */
    private static boolean isSessionExpired(Throwable e) {
        if (e == null) return false;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("session expired") || msg.contains("session_expired")
                || msg.contains("expired");
    }

    // ---- 生命周期 ----

    @Override
    public void close() {
        log.info("Closing session {} ({})", sessionId, label);
        stopListening();
        this.status = Status.CLOSED;
        dispatchPool.shutdown();
        try {
            if (!dispatchPool.awaitTermination(5, TimeUnit.SECONDS)) {
                dispatchPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dispatchPool.shutdownNow();
        }
        listenerPool.shutdownNow();
        try {
            if (!listenerPool.awaitTermination(3, TimeUnit.SECONDS)) {
                log.warn("Session {} listener pool did not terminate in time", sessionId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return String.format("WeChatSession[id=%s, label=%s, status=%s, botId=%s]",
                sessionId.substring(0, 8), label, status,
                getBotId() != null ? getBotId() : "N/A");
    }

    // ---- 内部类型 ----

    /** 轻量级会话状态，用于持久化。 */
    public record SessionState(
            String sessionId,
            String label,
            String botToken,
            String baseUrl,
            String botId,
            String userId,
            String cursor,
            String status
    ) {}
}
