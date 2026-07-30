package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRepository.class);
    private static final String DB_PATH = System.getProperty("user.home") + "/.youkeda/knowledge.db";

    @PostConstruct
    public void init() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS knowledge (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "text TEXT NOT NULL UNIQUE, " +
                "source TEXT DEFAULT '', " +
                "created_at TEXT DEFAULT (datetime('now','localtime')))");
            log.info("知识库持久化表初始化完成");
        } catch (SQLException e) {
            log.error("知识库持久化表初始化失败", e);
        }
    }

    Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    public List<String> findAll() {
        List<String> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT text FROM knowledge ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("text"));
            }
        } catch (SQLException e) {
            log.error("查询知识库失败", e);
        }
        return result;
    }

    public boolean exists(String text) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM knowledge WHERE text = ?")) {
            ps.setString(1, text);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("检查知识是否存在失败", e);
            return false;
        }
    }

    public void save(String text, String source) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO knowledge (text, source) VALUES (?, ?)")) {
            ps.setString(1, text);
            ps.setString(2, source != null ? source : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("保存知识失败", e);
        }
    }

    public void saveBatch(List<String> texts, String source) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO knowledge (text, source) VALUES (?, ?)")) {
            for (String text : texts) {
                ps.setString(1, text);
                ps.setString(2, source != null ? source : "");
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.error("批量保存知识失败", e);
        }
    }

    public int count() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM knowledge")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            log.error("统计知识数失败", e);
            return 0;
        }
    }
}
