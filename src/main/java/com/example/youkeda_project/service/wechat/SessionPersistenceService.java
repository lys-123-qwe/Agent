package com.example.youkeda_project.service.wechat;

import com.example.youkeda_project.config.SessionConfig;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Persists and restores WeChat sessions to/from JSON files on disk.
 */
@Service
public class SessionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SessionPersistenceService.class);

    private final Path persistDir;
    private final ObjectMapper objectMapper;

    public SessionPersistenceService(SessionConfig config) {
        this.persistDir = Paths.get(config.getPersistDir());
        this.objectMapper = new ObjectMapper();
    }

    // ---- public API ----

    public void save(WeChatSession session) {
        if (!session.isLoggedIn()) {
            log.warn("Session {} not logged in, skipping save", session.getSessionId());
            return;
        }

        ensureDir();
        SessionPersistData data = buildPersistData(session);

        Path file = sessionFile(session.getSessionId());
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
            log.info("Saved session {} ({}) to {}", session.getSessionId(),
                    session.getLabel(), file);
            updateIndex(session);
        } catch (Exception e) {
            log.error("Failed to save session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    public void saveAll(Collection<WeChatSession> sessions) {
        ensureDir();
        for (WeChatSession s : sessions) {
            save(s);
        }
        log.info("Saved {} sessions", sessions.size());
    }

    public void delete(String sessionId) {
        try {
            Files.deleteIfExists(sessionFile(sessionId));
            removeFromIndex(sessionId);
            log.info("Deleted persisted session {}", sessionId);
        } catch (IOException e) {
            log.warn("Failed to delete session file {}: {}", sessionId, e.getMessage());
        }
    }

    public List<SessionMeta> loadIndex() {
        Path indexFile = indexFile();
        if (!Files.exists(indexFile)) return List.of();

        try {
            String json = Files.readString(indexFile);
            List<SessionMeta> metas = objectMapper.readValue(json,
                    new TypeReference<List<SessionMeta>>() {});
            return metas != null ? metas : List.of();
        } catch (IOException e) {
            log.warn("Failed to load session index: {}", e.getMessage());
            return List.of();
        }
    }

    public WeChatSession.SessionState loadState(String sessionId) {
        Path file = sessionFile(sessionId);
        if (!Files.exists(file)) {
            log.warn("No persisted data for session {}", sessionId);
            return null;
        }

        try {
            String json = Files.readString(file);
            SessionPersistData data = objectMapper.readValue(json, SessionPersistData.class);
            if (data == null) return null;

            return new WeChatSession.SessionState(
                    data.sessionId,
                    data.label,
                    data.botToken,
                    data.baseUrl,
                    data.botId,
                    data.userId,
                    data.cursor != null ? data.cursor : "",
                    "LOGGED_IN"
            );
        } catch (IOException e) {
            log.error("Failed to load session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    public boolean exists(String sessionId) {
        return Files.exists(sessionFile(sessionId));
    }

    private void updateIndex(String sessionId, String label, String botId) {
        List<SessionMeta> metas = new ArrayList<>(loadIndex());
        metas.removeIf(m -> m.sessionId.equals(sessionId));
        metas.add(new SessionMeta(sessionId, label, botId, java.time.LocalDateTime.now().toString()));
        writeIndex(metas);
    }

    // ---- internal helpers ----

    private SessionPersistData buildPersistData(WeChatSession session) {
        SessionPersistData data = new SessionPersistData();
        data.sessionId = session.getSessionId();
        data.label = session.getLabel();
        data.savedAt = java.time.LocalDateTime.now().toString();
        data.botToken = session.getBotToken();
        data.baseUrl = session.getBaseUrl();
        data.botId = session.getBotId();
        data.userId = session.getUserId();
        data.cursor = session.getCursor();
        return data;
    }

    private void updateIndex(WeChatSession session) {
        List<SessionMeta> metas = new ArrayList<>(loadIndex());
        metas.removeIf(m -> m.sessionId.equals(session.getSessionId()));
        metas.add(new SessionMeta(
                session.getSessionId(),
                session.getLabel(),
                session.getBotId(),
                java.time.LocalDateTime.now().toString()));
        writeIndex(metas);
    }

    private void removeFromIndex(String sessionId) {
        List<SessionMeta> metas = new ArrayList<>(loadIndex());
        metas.removeIf(m -> m.sessionId.equals(sessionId));
        writeIndex(metas);
    }

    private void writeIndex(List<SessionMeta> metas) {
        ensureDir();
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(indexFile().toFile(), metas);
        } catch (Exception e) {
            log.warn("Failed to write session index: {}", e.getMessage());
        }
    }

    private void ensureDir() {
        try {
            Files.createDirectories(persistDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create persist dir: " + persistDir, e);
        }
    }

    private Path sessionFile(String sessionId) {
        return persistDir.resolve(sessionId + ".json");
    }

    private Path indexFile() {
        return persistDir.resolve("index.json");
    }

    // ---- data classes ----

    public record SessionMeta(String sessionId, String label, String botId, String savedAt) {}

    static class SessionPersistData {
        public String sessionId;
        public String label;
        public String savedAt;
        public String botToken;
        public String baseUrl;
        public String botId;
        public String userId;
        public String cursor;
    }
}
