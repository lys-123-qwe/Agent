package com.example.youkeda_project.repository;

import com.example.youkeda_project.ai.ChatMessage;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ChatRepository {

    private static final Logger log = LoggerFactory.getLogger(ChatRepository.class);

    @Value("${sqlite.db.path}")
    private String dbPath;

    private final ObjectMapper objectMapper;

    public ChatRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @PostConstruct
    public void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     TEXT NOT NULL,
                role        TEXT NOT NULL,
                content     TEXT,
                tool_call_id TEXT,
                tool_name   TEXT,
                tool_calls  TEXT,
                created_at  TEXT DEFAULT (datetime('now','localtime'))
            )
        """;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_user ON chat_messages(user_id)");
            log.info("SQLite 数据库初始化完成: {}", new java.io.File(dbPath).getAbsolutePath());
        } catch (SQLException e) {
            log.error("SQLite 数据库初始化失败", e);
        }
    }

    public void saveMessage(String userId, ChatMessage msg) {
        String sql = """
            INSERT INTO chat_messages (user_id, role, content, tool_call_id, tool_name, tool_calls)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, msg.role());
            ps.setString(3, serializeContent(msg.content()));
            ps.setString(4, msg.toolCallId());
            ps.setString(5, msg.name());
            ps.setString(6, serializeContent(msg.toolCalls()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("保存消息失败 user_id={}", userId, e);
        }
    }

    public List<ChatMessage> loadHistory(String userId, int limit) {
        String sql = """
            SELECT * FROM (
                SELECT * FROM chat_messages
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT ?
            ) ORDER BY id ASC
        """;
        List<ChatMessage> messages = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(toChatMessage(rs));
                }
            }
        } catch (SQLException e) {
            log.error("加载历史消息失败 user_id={}", userId, e);
        }
        return messages;
    }

    public void deleteUserHistory(String userId) {
        String sql = "DELETE FROM chat_messages WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
            log.info("已删除用户 {} 的对话历史", userId);
        } catch (SQLException e) {
            log.error("删除历史消息失败 user_id={}", userId, e);
        }
    }

    private String serializeContent(Object content) {
        if (content == null) return null;
        if (content instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            log.warn("序列化 content 失败", e);
            return content.toString();
        }
    }

    private Object deserializeContent(String text) {
        if (text == null) return null;
        if (text.startsWith("[")) {
            try {
                return objectMapper.readValue(text, List.class);
            } catch (Exception e) {
                log.warn("反序列化 content 失败，按纯文本处理", e);
                return text;
            }
        }
        return text;
    }

    private ChatMessage toChatMessage(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        Object content = deserializeContent(rs.getString("content"));
        String toolCallId = rs.getString("tool_call_id");
        String name = rs.getString("tool_name");
        Object toolCalls = deserializeContent(rs.getString("tool_calls"));
        return new ChatMessage(role, content, toolCallId, name, toolCalls);
    }
}
