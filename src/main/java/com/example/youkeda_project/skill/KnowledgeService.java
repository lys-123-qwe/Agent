package com.example.youkeda_project.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG knowledge service.
 *
 * Loads text documents from src/skills/{skillName}/ knowledge directory,
 * splits them into chunks, builds a simple inverted index, and retrieves
 * the top-K most relevant chunks for a query.
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final Path knowledgeRoot;
    private final Map<String, List<DocumentChunk>> skillDocuments = new ConcurrentHashMap<>();

    private static final int DEFAULT_TOP_K = 5;
    private static final int CHUNK_SIZE = 400;
    private static final int CHUNK_OVERLAP = 50;

    public KnowledgeService() {
        this.knowledgeRoot = Paths.get("src", "skills").toAbsolutePath().normalize();
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
     */
    public String buildContext(String skillName, String query) {
        List<String> chunks = retrieve(skillName, query);
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Relevant knowledge snippets:\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("--- snippet ").append(i + 1).append(" ---\n");
            sb.append(chunks.get(i)).append("\n");
        }
        sb.append("--- end of knowledge snippets ---\n");
        return sb.toString();
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
        int paraIdx = 0;

        for (String paragraph : text.split("\n\s*\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (buffer.length() + trimmed.length() + 2 > CHUNK_SIZE && buffer.length() > 0) {
                chunks.add(new DocumentChunk(skillName, docPath, buffer.toString().trim()));
                String tail = getOverlapTail(buffer.toString());
                buffer.setLength(0);
                buffer.append(tail);
            }
            buffer.append(trimmed).append("\n\n");
            paraIdx++;
        }

        if (buffer.length() > 0) {
            chunks.add(new DocumentChunk(skillName, docPath, buffer.toString().trim()));
        }

        return chunks;
    }

    private String getOverlapTail(String text) {
        if (text.length() <= CHUNK_OVERLAP) return "";
        int start = Math.max(0, text.length() - CHUNK_OVERLAP);
        return text.substring(start);
    }

    private record DocumentChunk(String skillName, String docPath, String content) {
        int wordCount() {
            return content.isBlank() ? 0 : content.split("\s+").length;
        }
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
