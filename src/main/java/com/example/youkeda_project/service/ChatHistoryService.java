package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final String DB_URL = "jdbc:sqlite:chat.db";

    private final DataSource dataSource;

    public ChatHistoryService() {
        this.dataSource = new SqliteDataSource();
        createTableIfNotExists();
    }

    private Connection getConn() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(true);
        return conn;
    }

    private void createTableIfNotExists() {
        try (Connection conn = getConn();
             var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chat_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT,
                    create_time TEXT DEFAULT (datetime('now','localtime'))
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_id ON chat_history(user_id)");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_memory (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    mem_key TEXT NOT NULL,
                    mem_value TEXT NOT NULL,
                    create_time TEXT DEFAULT (datetime('now','localtime'))
                )
            """);
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_mem_unique ON user_memory(user_id, mem_key, mem_value)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mem_user ON user_memory(user_id)");
            log.info("数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化数据库失败", e);
        }
    }

    public void saveMessage(String userId, String role, String content) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO chat_history (user_id, role, content) VALUES (?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("保存对话记录失败", e);
        }
    }

    public List<Map<String, Object>> getHistory(String userId, int limit) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT role, content, create_time FROM chat_history WHERE user_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("role", rs.getString("role"));
                    map.put("content", rs.getString("content"));
                    map.put("create_time", rs.getString("create_time"));
                    list.add(map);
                }
                return list;
            }
        } catch (Exception e) {
            log.warn("查询对话记录失败", e);
            return List.of();
        }
    }

    // ==================== 长期记忆 ====================

    public void saveMemory(String userId, String key, String value) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO user_memory (user_id, mem_key, mem_value) VALUES (?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, key);
            ps.setString(3, value);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                log.info("已保存记忆: {}={}", key, value);
            }
        } catch (Exception e) {
            log.warn("保存记忆失败", e);
        }
    }

    public Map<String, List<String>> getAllMemory(String userId) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT mem_key, mem_value FROM user_memory WHERE user_id = ? ORDER BY id")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("mem_key");
                    String value = rs.getString("mem_value");
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                }
            }
        } catch (Exception e) {
            log.warn("查询记忆失败", e);
        }
        return result;
    }

    public void deleteMemoryByKey(String userId, String key) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM user_memory WHERE user_id = ? AND mem_key = ?")) {
            ps.setString(1, userId);
            ps.setString(2, key);
            int rows = ps.executeUpdate();
            if (rows > 0) log.info("已覆盖记忆: {} (删除了 {} 条旧记录)", key, rows);
        } catch (Exception e) {
            log.warn("删除记忆失败", e);
        }
    }

    public void deleteMemory(String userId) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM user_memory WHERE user_id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("删除记忆失败", e);
        }
    }

    public void deleteHistory(String userId) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM chat_history WHERE user_id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
            log.info("已删除用户 {} 的对话记录", userId);
        } catch (Exception e) {
            log.warn("删除对话记录失败", e);
        }
    }

    static class SqliteDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(DB_URL);
        }
        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException();
        }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException { return null; }
        @Override public Object unwrap(Class iface) { return null; }
        @Override public boolean isWrapperFor(Class iface) { return false; }
    }
}
