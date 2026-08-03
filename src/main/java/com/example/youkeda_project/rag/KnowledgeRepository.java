package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
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
                "vector BLOB, " +
                "created_at TEXT DEFAULT (datetime('now','localtime')))");
            // 兼容旧表：添加 vector 列（如已存在则忽略）
            try { stmt.execute("ALTER TABLE knowledge ADD COLUMN vector BLOB"); }
            catch (SQLException ignored) {}
            log.info("知识库持久化表初始化完成");
        } catch (SQLException e) {
            log.error("知识库持久化表初始化失败", e);
        }
    }

    /** 序列化 float[] 向量为 BLOB（4 bytes/float） */
    public static byte[] serializeVector(float[] vector) {
        if (vector == null) return null;
        ByteBuffer buf = ByteBuffer.allocate(vector.length * 4);
        for (float v : vector) buf.putFloat(v);
        return buf.array();
    }

    /** 反序列化 BLOB 为 float[] 向量 */
    public static float[] deserializeVector(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) vector[i] = buf.getFloat();
        return vector;
    }

    Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    public record KnowledgeEntry(String text, float[] vector) {}

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

    public List<KnowledgeEntry> findAllWithVector() {
        List<KnowledgeEntry> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT text, vector FROM knowledge WHERE vector IS NOT NULL ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new KnowledgeEntry(rs.getString("text"),
                    deserializeVector(rs.getBytes("vector"))));
            }
        } catch (SQLException e) {
            log.error("查询知识库(含向量)失败", e);
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
        save(text, source, null);
    }

    public void save(String text, String source, float[] vector) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO knowledge (text, source, vector) VALUES (?, ?, ?)")) {
            ps.setString(1, text);
            ps.setString(2, source != null ? source : "");
            ps.setBytes(3, serializeVector(vector));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("保存知识失败", e);
        }
    }

    public void updateVector(String text, float[] vector) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE knowledge SET vector=? WHERE text=?")) {
            ps.setBytes(1, serializeVector(vector));
            ps.setString(2, text);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("更新向量失败", e);
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
