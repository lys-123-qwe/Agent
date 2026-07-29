package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 3;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public RagService(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void init() {
        log.info("RAG 初始化完成，知识库条目数: {}", vectorStore.size());
    }

    public List<String> retrieve(String query) {
        if (query == null || query.isBlank()) return List.of();
        try {
            float[] queryVec = embeddingService.embed(query);
            List<String> results = vectorStore.search(queryVec, TOP_K);
            log.info("RAG 检索: query=\"{}\", 命中={}条", query, results.size());
            return results;
        } catch (Exception e) {
            log.warn("RAG 检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    public String augment(String query, String systemPrompt) {
        List<String> snippets = retrieve(query);
        if (snippets.isEmpty()) return systemPrompt;
        StringBuilder sb = new StringBuilder(systemPrompt);
        sb.append("\n\n参考以下资料回答用户的问题：\n");
        for (int i = 0; i < snippets.size(); i++) {
            sb.append("---\n").append(snippets.get(i)).append("\n");
        }
        return sb.toString();
    }
}
