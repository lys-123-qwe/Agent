package com.example.youkeda_project.service.wechat;

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
/** 聊天记录与用户记忆（SQLite），提供 DataSource。 */


@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    // 固定到用户目录,IDEA / Maven / 命令行都不受工作目录影响
    private static final String DB_PATH = System.getProperty("user.home") + "/.youkeda/chat.db";

    private final DataSource dataSource;

    public ChatHistoryService() {
        // 确保数据库文件所在目录存在(SQLite JDBC 不会自动创建父目录)
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(
                    System.getProperty("user.home") + "/.youkeda"));
        } catch (java.io.IOException e) {
            throw new RuntimeException("无法创建数据目录", e);
        }
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

    static class SqliteDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
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
