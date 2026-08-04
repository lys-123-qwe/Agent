package com.example.youkeda_project.service.wechat;

import com.example.youkeda_project.config.SessionConfig;
import com.example.youkeda_project.protocol.WeixinMessage;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for multiple WeChat bot sessions.
 *
 * <p>Each session runs independently - its own IlinkProtocolClient,
 * thread pool, QR code, login state, and message listener.
 * Sessions are keyed by a unique sessionId (UUID).</p>
 */
@Service
public class WeChatSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WeChatSessionManager.class);

    private final ConcurrentHashMap<String, WeChatSession> sessions = new ConcurrentHashMap<>();
    private final SessionPersistenceService persistenceService;
    private final SessionConfig config;
    private final ObjectMapper objectMapper;

    /** The "default" session id - for backward-compatible single-bot usage. */
    private volatile String defaultSessionId;

    public WeChatSessionManager(SessionPersistenceService persistenceService,
                                SessionConfig config,
                                ObjectMapper objectMapper) {
        this.persistenceService = persistenceService;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    // ---- startup / shutdown ----

    @PostConstruct
    public void onStartup() {
        if (config.isAutoRestore()) {
            int count = restoreSessions();
            if (count > 0) {
                log.info("Auto-restored {} sessions on startup", count);
            }
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Shutting down - saving {} active sessions", sessions.size());
        saveAllSessions();
        closeAll();
    }

    // ---- login ----

    /**
     * Create a new session and start QR login.
     */
    public LoginResponse startLogin(String label) {
        if (sessions.size() >= config.getMaxSessions()) {
            throw new IllegalStateException(
                    "Max sessions (" + config.getMaxSessions() + ") reached. Close some first.");
        }

        WeChatSession session = WeChatSession.create(label, config, objectMapper);
        sessions.put(session.getSessionId(), session);

        String qrContent;
        try {
            qrContent = session.startLogin();
        } catch (Exception e) {
            sessions.remove(session.getSessionId());
            throw e;
        }

        // First session becomes default
        if (defaultSessionId == null) {
            defaultSessionId = session.getSessionId();
        }

        log.info("Created session {} ({}) - QR ready", session.getSessionId(), label);
        return new LoginResponse(session.getSessionId(), label, qrContent);
    }

    /**
     * Block until the user scans the QR code for this session.
     */
    public com.example.youkeda_project.protocol.LoginResult waitForLogin(String sessionId, long timeoutMs) throws Exception {
        WeChatSession session = requireSession(sessionId);
        return session.waitForLogin(timeoutMs);
    }

    // ---- messaging ----

    /**
     * Start message polling with auto-reply handlers for a session.
     */
    public void startListening(String sessionId,
                               WeChatSession.MessageHandler textHandler,
                               WeChatSession.ImageMessageHandler imageHandler,
                               WeChatSession.VoiceMessageHandler voiceHandler,
                               WeChatSession.FileMessageHandler fileHandler) {
        WeChatSession session = requireSession(sessionId);
        session.startListening(textHandler, imageHandler, voiceHandler, fileHandler);
    }

    /** Stop message polling for a session. */
    public void stopListening(String sessionId) {
        WeChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.stopListening();
        }
    }

    // ---- send ----

    public void sendText(String sessionId, String toUserId, String contextToken, String text) throws IOException {
        requireSession(sessionId).sendText(toUserId, contextToken, text);
    }

    public void sendImage(String sessionId, String toUserId, String contextToken, byte[] imageBytes) {
        requireSession(sessionId).sendImage(toUserId, contextToken, imageBytes);
    }

    public void sendFile(String sessionId, String toUserId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        requireSession(sessionId).sendFile(toUserId, contextToken, fileBytes, fileName, fileSize);
    }

    // ---- default session ----

    public String getDefaultSessionId() {
        return defaultSessionId;
    }

    public void setDefaultSessionId(String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        this.defaultSessionId = sessionId;
    }

    public WeChatSession getDefaultSession() {
        return defaultSessionId != null ? sessions.get(defaultSessionId) : null;
    }

    // ---- queries ----

    public WeChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Collection<WeChatSession> listSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public List<SessionInfo> listSessionInfos() {
        return sessions.values().stream()
                .map(s -> new SessionInfo(
                        s.getSessionId(),
                        s.getLabel(),
                        s.getStatus().name(),
                        s.getBotId(),
                        s.isListening(),
                        s.getCreatedAt(),
                        s.getLastActiveAt()))
                .sorted(Comparator.comparing(SessionInfo::createdAt))
                .toList();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    // ---- persistence ----

    public void saveSession(String sessionId) {
        WeChatSession session = sessions.get(sessionId);
        if (session != null) {
            persistenceService.save(session);
        }
    }

    public void saveAllSessions() {
        persistenceService.saveAll(sessions.values());
    }

    public int restoreSessions() {
        List<SessionPersistenceService.SessionMeta> metas = persistenceService.loadIndex();
        if (metas.isEmpty()) {
            log.info("No persisted sessions to restore");
            return 0;
        }

        int restored = 0;
        for (var meta : metas) {
            try {
                // 已恢复过则跳过,避免重复创建 session 导致 listener 冲突
                if (sessions.containsKey(meta.sessionId())) {
                    continue;
                }
                WeChatSession.SessionState state = persistenceService.loadState(meta.sessionId());
                if (state == null) {
                    log.warn("Could not load state for session {}", meta.sessionId());
                    continue;
                }

                WeChatSession session = WeChatSession.create(
                        state.sessionId(), state.label(), config, objectMapper,
                        state.botToken(), state.baseUrl(), state.cursor(), state.botId());

                sessions.put(session.getSessionId(), session);

                if (defaultSessionId == null) {
                    defaultSessionId = session.getSessionId();
                }

                restored++;
                log.info("Restored session {} ({}) botId={}",
                        session.getSessionId(), session.getLabel(), session.getBotId());
            } catch (Exception e) {
                log.error("Failed to restore session {}: {}", meta.sessionId(), e.getMessage());
            }
        }

        log.info("Restored {} sessions from disk", restored);
        return restored;
    }

    // ---- lifecycle ----

    public void closeSession(String sessionId) {
        WeChatSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            if (sessionId.equals(defaultSessionId)) {
                defaultSessionId = sessions.isEmpty() ? null
                        : sessions.values().iterator().next().getSessionId();
            }
        }
    }

    public void closeAll() {
        for (String id : new ArrayList<>(sessions.keySet())) {
            closeSession(id);
        }
    }

    public void deleteSession(String sessionId) {
        closeSession(sessionId);
        persistenceService.delete(sessionId);
    }

    // ---- helpers ----

    private WeChatSession requireSession(String sessionId) {
        WeChatSession s = sessions.get(sessionId);
        if (s == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return s;
    }

    // ---- types ----

    public record LoginResponse(String sessionId, String label, String qrContent) {}

    public record SessionInfo(
            String sessionId,
            String label,
            String status,
            String botId,
            boolean listening,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime lastActiveAt) {

        public String shortId() {
            return sessionId.length() > 8 ? sessionId.substring(0, 8) : sessionId;
        }
    }
}
