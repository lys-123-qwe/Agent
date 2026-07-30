package com.example.youkeda_project.service;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a single WeChat bot session - independent IlinkProtocolClient, thread pool,
 * QR code, login state, and message listener.
 *
 * <p>Each session is fully isolated: its own HTTP client, its own polling thread,
 * its own contextToken pool. Multiple sessions coexist without interference.</p>
 */
public class WeChatSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WeChatSession.class);

    /** Session lifecycle status. */
    public enum Status {
        /** QR code obtained, waiting for user to scan. */
        WAITING_QR,
        /** User scanned and login succeeded. */
        LOGGED_IN,
        /** QR code expired before scan. */
        EXPIRED,
        /** Login or runtime error. */
        ERROR,
        /** Session explicitly closed. */
        CLOSED
    }

    private final String sessionId;
    private final String label;
    private final IlinkProtocolClient client;
    private final ExecutorService listenerPool;    // single thread for getUpdates polling
    private final ExecutorService dispatchPool;    // thread pool for processing messages async
    private final SessionConfig sessionConfig;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final LocalDateTime createdAt;

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

    // ---- handler interfaces ----

    @FunctionalInterface
    public interface MessageHandler {
        /** @return reply text, or null to skip sending */
        String onMessage(String userId, String text, String contextToken);
    }

    @FunctionalInterface
    public interface ImageMessageHandler {
        /** @return reply text, or null to skip sending */
        String onImageMessage(String userId, String prompt, byte[] imageBytes, String contextToken);
    }

    @FunctionalInterface
    public interface VoiceMessageHandler {
        /** @return reply text, or null to skip sending */
        String onVoiceMessage(String userId, byte[] audioBytes, String contextToken);
    }

    @FunctionalInterface
    public interface FileMessageHandler {
        /** @return reply text, or null to skip sending */
        String onFileMessage(String userId, String fileName, byte[] fileBytes, String prompt, String contextToken);
    }

    // ---- factory ----

    /**
     * Create a new session with its own IlinkProtocolClient.
     */
    public static WeChatSession create(String label, SessionConfig config, ObjectMapper objectMapper) {
        return create(UUID.randomUUID().toString(), label, config, objectMapper, null, null, null, null);
    }

    /**
     * Create a session, optionally restoring from persisted state.
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

        // If restoring, set credentials immediately
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

    // ---- login flow ----

    /**
     * Start login: get QR code and begin async polling for scan.
     *
     * @return QR code content (URL to display)
     */
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

            // Start async polling for scan status
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
                            // continue polling
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
     * Block until login completes (user scans QR).
     *
     * @param timeoutMs max wait time in ms
     * @return LoginResult if login succeeded
     * @throws Exception on timeout or error
     */
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

    // ---- message listening ----

    /**
     * Start long-polling for WeChat messages in this session's dedicated thread.
     */
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

                    // Update cursor for next poll
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
                    // Session expired on server
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
     * Dispatch messages to handlers on the dispatch thread pool.
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
                if (msg.itemList() == null) return;

                String textPrompt = null;
                byte[] imageBytes = null;
                byte[] voiceBytes = null;
                byte[] fileBytes = null;
                String fileName = null;

                for (MessageItem item : msg.itemList()) {
                    // Extract text
                    if (item.textItem() != null && item.textItem().text() != null) {
                        textPrompt = item.textItem().text();
                    }
                    // Extract image
                    if (imageBytes == null && item.imageItem() != null) {
                        try {
                            imageBytes = client.downloadAndDecryptImage(item);
                        } catch (Exception e) {
                            log.error("Session {} download image failed: {}",
                                    sessionId, e.getMessage());
                        }
                    }
                    // Extract voice
                    if (voiceBytes == null && item.voiceItem() != null) {
                        try {
                            voiceBytes = client.downloadVoice(item.voiceItem());
                        } catch (Exception e) {
                            log.error("Session {} download voice failed: {}",
                                    sessionId, e.getMessage());
                        }
                    }
                    // Extract file
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

    public void stopListening() {
        if (listening.compareAndSet(true, false)) {
            log.info("Session {} ({}) stopping listener", sessionId, label);
        }
    }

    // ---- send methods ----

    public void sendText(String toUserId, String contextToken, String text) throws IOException {
        touch();
        client.sendMessage(toUserId, contextToken, List.of(MessageItem.text(text)));
    }

    public void sendImage(String toUserId, String contextToken, byte[] imageBytes) {
        touch();
        client.uploadAndSendImage(toUserId, contextToken, imageBytes);
    }

    public void sendVoice(String toUserId, String contextToken, byte[] audioBytes, int durationSeconds) {
        touch();
        client.uploadAndSendVoice(toUserId, contextToken, audioBytes, durationSeconds);
    }

    public void sendFile(String toUserId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        touch();
        client.uploadAndSendFile(toUserId, contextToken, fileBytes, fileName, fileSize);
    }

    public List<WeixinMessage> getUpdates() throws IOException {
        touch();
        GetUpdatesResponse resp = client.getUpdates(cursor);
        if (resp.getUpdatesBuf() != null && !resp.getUpdatesBuf().isBlank()) {
            this.cursor = resp.getUpdatesBuf();
        }
        return resp.msgs();
    }

    // ---- persistence ----

    /** Export state for persistence. */
    public SessionState exportState() {
        return new SessionState(sessionId, label, botToken, baseUrl, botId, userId, cursor, status.name());
    }

    // ---- accessors ----

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

    /** Check if an exception indicates the server-side session has expired. */
    private static boolean isSessionExpired(Throwable e) {
        if (e == null) return false;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("session expired") || msg.contains("session_expired")
                || msg.contains("expired");
    }

    // ---- lifecycle ----

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

    // ---- types ----

    /** Lightweight session state for persistence. */
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
