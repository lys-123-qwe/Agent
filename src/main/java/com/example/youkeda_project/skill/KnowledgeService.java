package com.example.youkeda_project.skill;

import com.example.youkeda_project.service.EmbeddingService;
import com.example.youkeda_project.service.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG knowledge service — BM25 + 向量语义检索。
 *
 * 加载 src/skills/{skillName}/knowledge/ 下的 markdown 文档，
 * 分块后同时建立 BM25 关键词索引和向量语义索引（Dashscope text-embedding-v3）。
 * 查询时优先向量检索，向量不可用时自动回退 BM25。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final Path knowledgeRoot;
    private final Map<String, List<DocumentChunk>> skillDocuments = new ConcurrentHashMap<>();
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ExecutorService indexingExecutor;

    private static final int DEFAULT_TOP_K = 5;
    private static final int CHUNK_SIZE = 400;
    private static final int CHUNK_OVERLAP = 50;

    private static final double VECTOR_FALLBACK_THRESHOLD = 0.3;

    public KnowledgeService(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.knowledgeRoot = Paths.get("src", "skills").toAbsolutePath().normalize();
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.indexingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "knowledge-indexer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Load documents for a given Skill.
     */
    public void loadSkillDocuments(String skillName, List<String> docPaths) {
        if (docPaths == null || docPaths.isEmpty()) return;

        Path skillDir = knowledgeRoot.resolve(skillName);
        List<DocumentChunk> chunks = new ArrayList<>();

        for (String docPath : docPaths) {
            Path path = skillDir.resolve(docPath).normalize();
            if (!path.startsWith(skillDir)) {
                log.warn("Unsafe document path skipped: {}", docPath);
                continue;
            }
            try {
                if (Files.isRegularFile(path)) {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    chunks.addAll(splitIntoChunks(skillName, docPath, text));
                } else {
                    log.warn("Knowledge document not found: {}", path);
                }
            } catch (IOException e) {
                log.warn("Failed to load knowledge document: {} - {}", path, e.getMessage());
            }
        }

        skillDocuments.put(skillName, chunks);
        log.info("Skill[{}] loaded {} knowledge chunks", skillName, chunks.size());

        // 异步生成向量索引（不阻塞启动）
        scheduleVectorIndexing(skillName, chunks);
    }

    /**
     * 异步构建向量索引。
     */
    private void scheduleVectorIndexing(String skillName, List<DocumentChunk> chunks) {
        // 检查是否已有向量缓存
        int existing = vectorStore.count(skillName);
        if (existing >= chunks.size()) {
            log.info("Skill[{}] 向量索引已存在 ({} 条)，跳过重建", skillName, existing);
            return;
        }

        indexingExecutor.submit(() -> {
            try {
                // 转换为 VectorStore.DocumentChunk
                List<VectorStore.DocumentChunk> vChunks = new ArrayList<>();
                for (DocumentChunk c : chunks) {
                    vChunks.add(new VectorStore.DocumentChunk(c.docPath, c.chunkIdx, c.content));
                }
                vectorStore.store(skillName, vChunks, embeddingService);
            } catch (Exception e) {
                log.warn("Skill[{}] 向量索引构建失败（将回退 BM25）: {}", skillName, e.getMessage());
            }
        });
    }

    /** 清除指定 skill 的知识（支持热更新重载） */
    public void clearSkillDocuments(String skillName) {
        skillDocuments.remove(skillName);
    }

    /**
     * Retrieve top-K relevant chunks for a Skill and query.
     */
    public List<String> retrieve(String skillName, String query) {
        return retrieve(skillName, query, DEFAULT_TOP_K);
    }

    public List<String> retrieve(String skillName, String query, int topK) {
        List<DocumentChunk> docs = skillDocuments.get(skillName);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return docs.stream().limit(topK).map(d -> d.content).toList();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk doc : docs) {
            Set<String> docTokens = tokenize(doc.content);
            double score = bm25Score(queryTokens, docTokens, docs.size(), doc.wordCount());
            scored.add(new ScoredChunk(doc, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.stream()
                .limit(topK)
                .map(s -> s.chunk.content)
                .collect(Collectors.toList());
    }

    /**
     * Build an augmented context string from retrieved knowledge.
     * 优先向量语义检索，向量不可用时自动回退 BM25。
     */
    public String buildContext(String skillName, String query) {
        // 1. 尝试向量检索
        List<String> chunks = retrieveVector(skillName, query, DEFAULT_TOP_K);

        // 2. 向量检索为空或质量不足 → 回退 BM25
        if (chunks.isEmpty()) {
            log.debug("Skill[{}] 向量检索为空，回退 BM25", skillName);
            chunks = retrieve(skillName, query);
        }

        if (chunks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("Relevant knowledge snippets:\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("--- snippet ").append(i + 1).append(" ---\n");
            sb.append(chunks.get(i)).append("\n");
        }
        sb.append("--- end of knowledge snippets ---\n");
        return sb.toString();
    }

    /**
     * 向量语义检索。
     * 将查询文本转为 embedding，在 SQLite 中按余弦相似度搜索。
     *
     * @return topK 最相关的 chunk 内容列表，向量不可用时返回空列表
     */
    public List<String> retrieveVector(String skillName, String query, int topK) {
        if (query == null || query.isBlank()) return List.of();

        try {
            float[] queryVec = embeddingService.embed(query);
            if (queryVec.length == 0) {
                log.debug("Embedding 生成失败（API Key 未配或网络异常），回退 BM25");
                return List.of();
            }
            return vectorStore.search(skillName, queryVec, topK);
        } catch (Exception e) {
            log.warn("向量检索异常，回退 BM25: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Compute BM25-like score for a document against a query.
     */
    private double bm25Score(Set<String> queryTokens, Set<String> docTokens,
                             int totalDocs, int docWordCount) {
        final double k1 = 1.5;
        final double b = 0.75;
        double avgDocLen = computeAvgDocLen(totalDocs);

        double score = 0;
        for (String token : queryTokens) {
            if (!docTokens.contains(token)) continue;

            long docsWithToken = countDocsContaining(token);
            double idf = Math.log(1 + (totalDocs - docsWithToken + 0.5) / (docsWithToken + 0.5));
            double tf = 1.0;
            double denom = tf + k1 * (1 - b + b * (docWordCount / avgDocLen));
            score += idf * (tf * (k1 + 1)) / denom;
        }
        return score;
    }

    private double computeAvgDocLen(int totalDocs) {
        if (totalDocs == 0) return 1.0;
        int total = 0;
        for (List<DocumentChunk> chunks : skillDocuments.values()) {
            for (DocumentChunk chunk : chunks) {
                total += chunk.wordCount();
            }
        }
        return Math.max(1.0, (double) total / totalDocs);
    }

    private long countDocsContaining(String token) {
        long count = 0;
        for (List<DocumentChunk> chunks : skillDocuments.values()) {
            for (DocumentChunk chunk : chunks) {
                if (tokenize(chunk.content).contains(token)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /**
     * Tokenize text: keep CJK chars and alphanumeric sequences.
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) return tokens;

        StringBuilder sb = new StringBuilder();
        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) || (c >= '\u4e00' && c <= '\u9fff')) {
                sb.append(c);
            } else {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    /**
     * Split document text into overlapping chunks.
     */
    private List<DocumentChunk> splitIntoChunks(String skillName, String docPath, String text) {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkIdx = 0;

        for (String paragraph : text.split("\n\s*\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (buffer.length() + trimmed.length() + 2 > CHUNK_SIZE && buffer.length() > 0) {
                chunks.add(new DocumentChunk(skillName, docPath, chunkIdx++, buffer.toString().trim()));
                String tail = getOverlapTail(buffer.toString());
                buffer.setLength(0);
                buffer.append(tail);
            }
            buffer.append(trimmed).append("\n\n");
        }

        if (buffer.length() > 0) {
            chunks.add(new DocumentChunk(skillName, docPath, chunkIdx++, buffer.toString().trim()));
        }

        return chunks;
    }

    private String getOverlapTail(String text) {
        if (text.length() <= CHUNK_OVERLAP) return "";
        int start = Math.max(0, text.length() - CHUNK_OVERLAP);
        return text.substring(start);
    }

    private static class DocumentChunk {
        final String skillName;
        final String docPath;
        final int chunkIdx;
        final String content;

        DocumentChunk(String skillName, String docPath, int chunkIdx, String content) {
            this.skillName = skillName;
            this.docPath = docPath;
            this.chunkIdx = chunkIdx;
            this.content = content;
        }

        int wordCount() {
            return content.isBlank() ? 0 : content.split("\s+").length;
        }
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
