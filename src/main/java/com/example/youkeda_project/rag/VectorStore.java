package com.example.youkeda_project.rag;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    private final EmbeddingService embeddingService;
    private final List<Entry> entries = new ArrayList<>();

    public VectorStore(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(
                new ClassPathResource("knowledge.json").getInputStream());
            List<String> loaded = mapper.convertValue(root.get("entries"),
                new TypeReference<List<String>>() {});
            loaded.forEach(this::add);
            log.info("知识库初始化完成: {} 条知识 (来自 knowledge.json)", entries.size());
        } catch (Exception e) {
            log.error("加载 knowledge.json 失败，使用内置知识", e);
            add("投资有风险，入市需谨慎。以上信息仅供参考，不构成投资建议。历史表现不代表未来收益。");
        }
    }

    public void add(String text) {
        entries.add(new Entry(text));
    }

    public void addWithVector(String text, float[] vector) {
        Entry e = new Entry(text);
        e.vector = vector;
        entries.add(e);
    }

    public List<String> search(float[] queryVec, int topK) {
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.vector == null) {
                e.vector = embeddingService.embed(e.text);
            }
            scored.add(new Scored(i, cosineSimilarity(queryVec, e.vector)));
        }
        scored.sort(Comparator.<Scored>comparingDouble(s -> s.score).reversed());
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(entries.get(scored.get(i).idx).text);
        }
        return results;
    }

    public int size() { return entries.size(); }

    public double similarityTo(int index, float[] queryVec) {
        if (index < 0 || index >= entries.size()) return 0;
        Entry e = entries.get(index);
        if (e.vector == null) {
            e.vector = embeddingService.embed(e.text);
        }
        return cosineSimilarity(queryVec, e.vector);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-10);
    }

    private static class Entry {
        final String text; float[] vector;
        Entry(String text) { this.text = text; }
    }
    private record Scored(int idx, double score) {}
}
