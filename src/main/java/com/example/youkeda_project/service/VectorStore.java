package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 向量存储。
 * <p>
 * 将文本片段的 Embedding 向量以 BLOB 形式存储在 SQLite 中，
 * 查询时遍历计算余弦相似度取 topK。
 * <p>
 * 与 ChatHistoryService 共用同一个数据库文件 (~/.youkeda/chat.db)，
 * 使用独立表 knowledge_embeddings。
 */
@Service
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    /** 与 ChatHistoryService 共用数据库 */
    private static final String DB_PATH = System.getProperty("user.home") + "/.youkeda/chat.db";

    public VectorStore() {
        createTableIfNotExists();
    }

    private Connection getConn() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        conn.setAutoCommit(true);
        return conn;
    }

    private void createTableIfNotExists() {
        try (Connection conn = getConn(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_embeddings (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    skill_name  TEXT NOT NULL,
                    doc_path    TEXT NOT NULL,
                    chunk_idx   INTEGER NOT NULL,
                    content     TEXT NOT NULL,
                    embedding   BLOB,
                    created_at  TEXT DEFAULT (datetime('now','localtime'))
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_kemb_skill ON knowledge_embeddings(skill_name)");
            log.info("向量存储表初始化完成");
        } catch (Exception e) {
            log.error("初始化向量存储表失败", e);
        }
    }

    /**
     * 存储一个 skill 的所有文档分块及其 embedding。
     * <p>
     * 先清空该 skill 的旧数据，再批量生成 embedding 后写入。
     */
    public void store(String skillName, List<DocumentChunk> chunks, EmbeddingService embeddingService) {
        if (chunks == null || chunks.isEmpty()) return;

        // 清空旧数据
        clearSkill(skillName);

        // 收集所有文本
        List<String> texts = new ArrayList<>();
        for (DocumentChunk c : chunks) texts.add(c.content);

        // 批量生成 embedding
        log.info("为 Skill[{}] 生成 {} 条 embedding...", skillName, texts.size());
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // 写入数据库
        int stored = 0;
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO knowledge_embeddings (skill_name, doc_path, chunk_idx, content, embedding) VALUES (?, ?, ?, ?, ?)")) {

            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = chunks.get(i);
                float[] vec = i < embeddings.size() ? embeddings.get(i) : new float[0];

                ps.setString(1, skillName);
                ps.setString(2, chunk.docPath);
                ps.setInt(3, chunk.chunkIdx);
                ps.setString(4, chunk.content);
                ps.setBytes(5, vec.length > 0 ? EmbeddingService.toBytes(vec) : null);
                ps.addBatch();
                stored++;
            }
            ps.executeBatch();
            log.info("Skill[{}] 向量存储完成: {} 条", skillName, stored);

        } catch (Exception e) {
            log.error("存储向量失败: skillName={}", skillName, e);
        }
    }

    /**
     * 语义检索：用查询向量与 skill 下的所有 chunk 计算余弦相似度，返回 topK 内容。
     *
     * @param skillName 限定的 skill 名称
     * @param queryVec  查询文本的 embedding 向量
     * @param topK      返回数量
     * @return 按相似度降序排列的 chunk 内容列表
     */
    public List<String> search(String skillName, float[] queryVec, int topK) {
        if (queryVec == null || queryVec.length == 0) return List.of();

        List<ScoredChunk> scored = new ArrayList<>();

        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT content, embedding FROM knowledge_embeddings WHERE skill_name = ? AND embedding IS NOT NULL")) {

            ps.setString(1, skillName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content");
                    byte[] embBytes = rs.getBytes("embedding");
                    if (embBytes == null || embBytes.length == 0) continue;

                    float[] vec = EmbeddingService.fromBytes(embBytes);
                    double sim = EmbeddingService.cosineSimilarity(queryVec, vec);
                    scored.add(new ScoredChunk(content, sim));
                }
            }

        } catch (Exception e) {
            log.error("向量检索失败: skillName={}", skillName, e);
            return List.of();
        }

        // 排序取 topK
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(scored.get(i).content);
        }

        log.debug("Skill[{}] 向量检索: {} 个候选 → top{} 结果, bestScore={:.4f}",
                skillName, scored.size(), results.size(),
                scored.isEmpty() ? 0 : scored.get(0).score);
        return results;
    }

    /**
     * 获取某 skill 已存储的 chunk 数量。
     */
    public int count(String skillName) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM knowledge_embeddings WHERE skill_name = ?")) {
            ps.setString(1, skillName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /** 清空某 skill 的所有向量数据 */
    private void clearSkill(String skillName) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM knowledge_embeddings WHERE skill_name = ?")) {
            ps.setString(1, skillName);
            int deleted = ps.executeUpdate();
            if (deleted > 0) log.debug("Skill[{}] 清除旧向量 {} 条", skillName, deleted);
        } catch (Exception e) {
            log.warn("清除旧向量失败: skillName={}", skillName, e);
        }
    }

    /** 清空所有向量数据（调试用） */
    public void clearAll() {
        try (Connection conn = getConn(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM knowledge_embeddings");
            log.info("所有向量数据已清空");
        } catch (Exception e) {
            log.warn("清空向量数据失败", e);
        }
    }

    // ==================== 内部类型 ====================

    private record ScoredChunk(String content, double score) {}

    /**
     * 文档分块，供 VectorStore.store() 使用。
     */
    public record DocumentChunk(String docPath, int chunkIdx, String content) {}
}
