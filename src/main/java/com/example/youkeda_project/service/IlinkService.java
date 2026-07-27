package com.example.youkeda_project.service;

import com.example.youkeda_project.protocol.FileItem;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.VoiceItem;
import com.example.youkeda_project.protocol.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WeChat iLink service — now delegates to {@link WeChatSessionManager} for
 * multi-session support.
 *
 * <p><b>Default session (backward-compatible):</b>
 * Calling {@link #login()} / {@link #startListening startListening(...)} / {@link #sendText sendText(...)}
 * without a sessionId operates on the "default" session — the first one created.</p>
 *
 * <p><b>Multi-session:</b>
 * Use the sessionId overloads for multi-bot scenarios. Each session has its own
 * IlinkProtocolClient, thread pool, QR code, and contextToken.</p>
 */
@Service
public class IlinkService {

    private static final Logger log = LoggerFactory.getLogger(IlinkService.class);

    private final WeChatSessionManager sessionManager;

    public IlinkService(WeChatSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    // ======================== Single-bot API (default session) ========================

    /**
     * Start login for the default session. Creates one if needed.
     *
     * @return QR code content
     */
    public synchronized String login() {
        WeChatSession existing = sessionManager.getDefaultSession();
        if (existing != null && existing.isLoggedIn() && existing.isListening()) {
            return "Already logged in as " + existing.getBotId()
                    + ". Use 'wechat login <label>' to add another bot.";
        }
        if (existing != null && existing.isLoggedIn() && !existing.isListening()) {
            log.info("Default session {} is logged in but not listening — replacing",
                    existing.getSessionId().substring(0, 8));
            sessionManager.closeSession(existing.getSessionId());
        }
        if (existing != null && existing.getStatus() == WeChatSession.Status.WAITING_QR) {
            return "QR login already in progress. Scan the QR code, or wait for timeout.";
        }

        WeChatSessionManager.LoginResponse resp = sessionManager.startLogin("default");
        sessionManager.setDefaultSessionId(resp.sessionId());
        log.info("Default session {} created, QR ready", resp.sessionId());
        return resp.qrContent();
    }

    /**
     * Start login with a custom label (for multi-bot).
     */
    public WeChatSessionManager.LoginResponse login(String label) {
        return sessionManager.startLogin(label);
    }

    /**
     * Always create a fresh login session and make it the default.
     */
    public WeChatSessionManager.LoginResponse loginNew() {
        var resp = sessionManager.startLogin("default");
        sessionManager.setDefaultSessionId(resp.sessionId());
        return resp;
    }

    public com.example.youkeda_project.protocol.LoginResult waitForLogin(long timeoutMs) throws Exception {
        String sid = sessionManager.getDefaultSessionId();
        if (sid == null) throw new IllegalStateException("No login in progress. Call login() first.");
        return sessionManager.waitForLogin(sid, timeoutMs);
    }

    public com.example.youkeda_project.protocol.LoginResult waitForLogin(String sessionId, long timeoutMs) throws Exception {
        return sessionManager.waitForLogin(sessionId, timeoutMs);
    }

    // ---- messaging (default session) ----

    public List<WeixinMessage> getUpdates() {
        String sid = requireDefaultSession();
        try {
            return sessionManager.getUpdates(sid);
        } catch (java.io.IOException e) {
            throw new RuntimeException("getUpdates failed: " + e.getMessage(), e);
        }
    }

    public List<WeixinMessage> getUpdates(String sessionId) throws java.io.IOException {
        return sessionManager.getUpdates(sessionId);
    }

    public void sendText(String userId, String contextToken, String text) {
        try {
            sessionManager.sendText(requireDefaultSession(), userId, contextToken, text);
        } catch (java.io.IOException e) {
            throw new RuntimeException("sendText failed: " + e.getMessage(), e);
        }
    }

    public void sendText(String sessionId, String userId, String contextToken, String text) throws java.io.IOException {
        sessionManager.sendText(sessionId, userId, contextToken, text);
    }

    public void sendImage(String userId, String contextToken, byte[] imageBytes) {
        sessionManager.sendImage(requireDefaultSession(), userId, contextToken, imageBytes);
    }

    public void sendImage(String sessionId, String userId, String contextToken, byte[] imageBytes) {
        sessionManager.sendImage(sessionId, userId, contextToken, imageBytes);
    }

    public void sendVoice(String userId, String contextToken, byte[] audioBytes, int durationSeconds) {
        sessionManager.sendVoice(requireDefaultSession(), userId, contextToken, audioBytes, durationSeconds);
    }

    public void sendFile(String userId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        sessionManager.sendFile(requireDefaultSession(), userId, contextToken, fileBytes, fileName, fileSize);
    }

    public void sendFile(String sessionId, String userId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        sessionManager.sendFile(sessionId, userId, contextToken, fileBytes, fileName, fileSize);
    }

    // ---- listener (default session) ----

    public synchronized void startListening(String sessionId,
                                            WeChatSession.MessageHandler textHandler,
                                            WeChatSession.ImageMessageHandler imageHandler,
                                            WeChatSession.VoiceMessageHandler voiceHandler,
                                            WeChatSession.FileMessageHandler fileHandler) {
        sessionManager.startListening(sessionId, textHandler, imageHandler, voiceHandler, fileHandler);
    }

    public void stopListening() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid != null) sessionManager.stopListening(sid);
    }

    public void stopListening(String sessionId) {
        sessionManager.stopListening(sessionId);
    }

    // ---- status (default session) ----

    public boolean isConnected() {
        WeChatSession s = sessionManager.getDefaultSession();
        return s != null && s.isLoggedIn();
    }

    public boolean isConnected(String sessionId) {
        WeChatSession s = sessionManager.getSession(sessionId);
        return s != null && s.isLoggedIn();
    }

    public boolean isListening() {
        WeChatSession s = sessionManager.getDefaultSession();
        return s != null && s.isListening();
    }

    public boolean isListening(String sessionId) {
        WeChatSession s = sessionManager.getSession(sessionId);
        return s != null && s.isListening();
    }

    public String getBotId() {
        WeChatSession s = sessionManager.getDefaultSession();
        return s != null ? s.getBotId() : null;
    }

    // ---- session management ----

    public List<WeChatSessionManager.SessionInfo> listSessions() {
        return sessionManager.listSessionInfos();
    }

    public int getSessionCount() {
        return sessionManager.getSessionCount();
    }

    public void switchDefaultSession(String sessionId) {
        sessionManager.setDefaultSessionId(sessionId);
        System.out.println("Switched to session " + sessionId.substring(0, 8));
    }

    public WeChatSessionManager getSessionManager() {
        return sessionManager;
    }

    public void saveAll() {
        sessionManager.saveAllSessions();
        System.out.println("All sessions saved.");
    }

    public int restoreAll() {
        return sessionManager.restoreSessions();
    }

    public void closeSession(String sessionId) {
        sessionManager.closeSession(sessionId);
    }

    public void deleteSession(String sessionId) {
        sessionManager.deleteSession(sessionId);
    }

    // ---- lifecycle (default session) ----

    public synchronized void logout() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid != null) {
            sessionManager.closeSession(sid);
        }
    }

    public synchronized void logoutAll() {
        sessionManager.closeAll();
        System.out.println("All sessions logged out.");
    }

    // ---- utility methods (used by CliRunner) ----

    /** Extract first text from a message. */
    public static String extractText(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.textItem() != null)
                .findFirst()
                .map(item -> item.textItem().text())
                .orElse(null);
    }

    /** Extract first image item from a message. */
    public static MessageItem extractFirstImage(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.imageItem() != null)
                .findFirst()
                .orElse(null);
    }

    /** Extract first voice item from a message. */
    public static MessageItem extractFirstVoice(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.voiceItem() != null)
                .findFirst()
                .orElse(null);
    }

    /** Extract first file item from a message. */
    public static MessageItem extractFirstFile(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.fileItem() != null)
                .findFirst()
                .orElse(null);
    }

    /** Translate voice recognition technical errors to user-friendly messages. */
    public static String getVoiceErrorMessage(String technicalError) {
        if (technicalError.contains("音频数据为空") || technicalError.contains("The audio is empty")) {
            return "抱歉，我没有听清您说的话，请再试一次（语音不要太短）";
        }
        if (technicalError.contains("未能从响应中提取识别文本")) {
            return "抱歉，语音识别没有返回结果，请再试一次";
        }
        if (technicalError.contains("未配置阿里云 API Key")) {
            return "语音识别服务暂时不可用，请稍后再试";
        }
        if (technicalError.contains("语音识别失败")) {
            return "语音识别失败，请再试一次";
        }
        return "抱歉，语音识别出现问题，请再试一次";
    }

    // ---- helpers ----

    private String requireDefaultSession() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid == null) {
            throw new IllegalStateException("No session. Call login() first.");
        }
        WeChatSession s = sessionManager.getSession(sid);
        if (s == null || !s.isLoggedIn()) {
            throw new IllegalStateException("Not logged in. Call login() first.");
        }
        return sid;
    }
}
