package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一知识持久化：通用知识与 skill 文档知识都存同一个 knowledge.db。
 * skill_name 为 NULL 表示通用知识，否则表示属于某个 skill 的文档知识。
 */

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
                "skill_name TEXT DEFAULT NULL, " +
                "created_at TEXT DEFAULT (datetime('now','localtime')))");
            try { stmt.execute("ALTER TABLE knowledge ADD COLUMN vector BLOB"); }
            catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE knowledge ADD COLUMN skill_name TEXT DEFAULT NULL"); }
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

    public record KnowledgeEntry(String text, float[] vector, String skillName) {}

    // ---- 通用知识(skill_name IS NULL) ----

    public List<String> findAll() {
        return findAll(null);
    }

    public List<KnowledgeEntry> findAllWithVector() {
        return findAllWithVector(null);
    }

    public int count() {
        return count(null);
    }

    public void save(String text, String source) {
        save(text, source, null, null);
    }

    public void save(String text, String source, float[] vector) {
        save(text, source, vector, null);
    }

    public void saveBatch(List<String> texts, String source) {
        saveBatch(texts, source, null);
    }

    // ---- skill 知识(指定 skill_name) ----

    public List<String> findAll(String skillName) {
        List<String> result = new ArrayList<>();
        String sql = skillName == null
            ? "SELECT text FROM knowledge WHERE skill_name IS NULL ORDER BY id"
            : "SELECT text FROM knowledge WHERE skill_name = ? ORDER BY id";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (skillName != null) ps.setString(1, skillName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString("text"));
            }
        } catch (SQLException e) {
            log.error("查询知识库失败: skillName={}", skillName, e);
        }
        return result;
    }

    public List<KnowledgeEntry> findAllWithVector(String skillName) {
        List<KnowledgeEntry> result = new ArrayList<>();
        String sql = skillName == null
            ? "SELECT text, vector FROM knowledge WHERE skill_name IS NULL AND vector IS NOT NULL ORDER BY id"
            : "SELECT text, vector FROM knowledge WHERE skill_name = ? AND vector IS NOT NULL ORDER BY id";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (skillName != null) ps.setString(1, skillName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new KnowledgeEntry(rs.getString("text"),
                        deserializeVector(rs.getBytes("vector")), skillName));
                }
            }
        } catch (SQLException e) {
            log.error("查询知识库(含向量)失败: skillName={}", skillName, e);
        }
        return result;
    }

    public int count(String skillName) {
        String sql = skillName == null
            ? "SELECT COUNT(*) FROM knowledge WHERE skill_name IS NULL"
            : "SELECT COUNT(*) FROM knowledge WHERE skill_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (skillName != null) ps.setString(1, skillName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            log.error("统计知识数失败: skillName={}", skillName, e);
            return 0;
        }
    }

    public void save(String text, String source, float[] vector, String skillName) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO knowledge (text, source, vector, skill_name) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, text);
            ps.setString(2, source != null ? source : "");
            ps.setBytes(3, serializeVector(vector));
            ps.setString(4, skillName);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("保存知识失败", e);
        }
    }

    public void saveBatch(List<String> texts, String source, String skillName) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO knowledge (text, source, skill_name) VALUES (?, ?, ?)")) {
            for (String text : texts) {
                ps.setString(1, text);
                ps.setString(2, source != null ? source : "");
                ps.setString(3, skillName);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.error("批量保存知识失败", e);
        }
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

    /** 按来源删除知识（如清理误入库的 topics.md 条目） */
    public void deleteBySource(String source) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM knowledge WHERE source = ?")) {
            ps.setString(1, source);
            int deleted = ps.executeUpdate();
            if (deleted > 0) log.info("按来源清理知识 {} 条: {}", deleted, source);
        } catch (SQLException e) {
            log.error("按来源删除知识失败: {}", source, e);
        }
    }
}
