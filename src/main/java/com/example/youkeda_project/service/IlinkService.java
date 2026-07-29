package com.example.youkeda_project.service;

import com.example.youkeda_project.protocol.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * WeChat iLink service - 统一委托给 {@link WeChatSessionManager},
 * 支持多 session 登录,消息监听,发送.
 *
 * <p>启动时自动加载 {@link com.example.youkeda_project.skill.SkillManager}
 * 动态扫描 src/skills/* 注册 Skill,不依赖 if-else 硬编码.</p>
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
            log.info("Default session {} is logged in but not listening - replacing",
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

    // ---- messaging ----

    public List<WeixinMessage> getUpdates() throws IOException {
        String sid = requireDefaultSessionId();
        return sessionManager.getUpdates(sid);
    }

    public List<WeixinMessage> getUpdates(String sessionId) throws IOException {
        return sessionManager.getUpdates(sessionId);
    }

    /**
     * Start message polling with auto-reply handlers for the default session.
     */
    public void startListening(WeChatSession.MessageHandler textHandler,
                               WeChatSession.ImageMessageHandler imageHandler,
                               WeChatSession.VoiceMessageHandler voiceHandler,
                               WeChatSession.FileMessageHandler fileHandler) {
        String sid = requireDefaultSessionId();
        sessionManager.startListening(sid, textHandler, imageHandler, voiceHandler, fileHandler);
    }

    public void startListening(String sessionId,
                               WeChatSession.MessageHandler textHandler,
                               WeChatSession.ImageMessageHandler imageHandler,
                               WeChatSession.VoiceMessageHandler voiceHandler,
                               WeChatSession.FileMessageHandler fileHandler) {
        sessionManager.startListening(sessionId, textHandler, imageHandler, voiceHandler, fileHandler);
    }

    public void stopListening() {
        String sid = requireDefaultSessionId();
        sessionManager.stopListening(sid);
    }

    public void stopListening(String sessionId) {
        sessionManager.stopListening(sessionId);
    }

    // ---- send ----

    public void sendText(String userId, String contextToken, String text) throws IOException {
        String sid = requireDefaultSessionId();
        sessionManager.sendText(sid, userId, contextToken, text);
    }

    public void sendText(String sessionId, String userId, String contextToken, String text) throws IOException {
        sessionManager.sendText(sessionId, userId, contextToken, text);
    }

    public void sendImage(String userId, String contextToken, byte[] imageBytes) {
        String sid = requireDefaultSessionId();
        sessionManager.sendImage(sid, userId, contextToken, imageBytes);
    }

    public void sendImage(String sessionId, String userId, String contextToken, byte[] imageBytes) {
        sessionManager.sendImage(sessionId, userId, contextToken, imageBytes);
    }

    public void sendVoice(String userId, String contextToken, byte[] audioBytes, int durationSeconds) {
        String sid = requireDefaultSessionId();
        sessionManager.sendVoice(sid, userId, contextToken, audioBytes, durationSeconds);
    }

    public void sendVoice(String sessionId, String userId, String contextToken, byte[] audioBytes, int durationSeconds) {
        sessionManager.sendVoice(sessionId, userId, contextToken, audioBytes, durationSeconds);
    }

    public void sendFile(String userId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        String sid = requireDefaultSessionId();
        sessionManager.sendFile(sid, userId, contextToken, fileBytes, fileName, fileSize);
    }

    public void sendFile(String sessionId, String userId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        sessionManager.sendFile(sessionId, userId, contextToken, fileBytes, fileName, fileSize);
    }

    // ---- session management ----

    public WeChatSessionManager getSessionManager() {
        return sessionManager;
    }

    public WeChatSession getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    public void switchDefaultSession(String sessionId) {
        sessionManager.setDefaultSessionId(sessionId);
    }

    public void deleteSession(String sessionId) {
        sessionManager.closeSession(sessionId);
    }

    public void logout() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid != null) {
            sessionManager.closeSession(sid);
        }
    }

    public void logoutAll() {
        sessionManager.closeAll();
    }

    public int restoreAll() {
        return sessionManager.restoreSessions();
    }

    public void saveAll() {
        sessionManager.saveAllSessions();
    }

    public int getSessionCount() {
        return sessionManager.getSessionCount();
    }

    public List<WeChatSessionManager.SessionInfo> listSessions() {
        return sessionManager.listSessionInfos();
    }

    // ---- helpers ----

    private String requireDefaultSessionId() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid == null) {
            throw new IllegalStateException("No default session. Call login() first.");
        }
        return sid;
    }

    /**
     * 语音转文字失败时,返回给用户的友好提示.
     */
    public static String getVoiceErrorMessage(String error) {
        if (error == null) return "语音识别暂时不可用,请稍后再试或发送文字消息.";
        if (error.contains("Vosk") || error.contains("model") || error.contains("模型")) {
            return "语音识别模型还没准备好,请稍后再试.";
        }
        if (error.contains("FFmpeg") || error.contains("ffmpeg")) {
            return "语音格式转换失败,请检查 FFmpeg 是否配置正确.";
        }
        return "语音识别失败,请尝试发送文字消息.";
    }
}
