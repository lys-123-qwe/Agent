package com.example.youkeda_project.rag;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.example.youkeda_project.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KnowledgeCollector {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCollector.class);
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "\\d+\\.\\s*(.*?)\\s*\\[详情]\\((https?://[^)]+)\\)");

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final NewsService newsService;
    private final ObjectMapper objectMapper;

    public KnowledgeCollector(EmbeddingService embeddingService, VectorStore vectorStore,
                              NewsService newsService, ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.newsService = newsService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(timeUnit = TimeUnit.HOURS, fixedRate = 24)
    public void collect() {
        List<String> topics = loadTopics();
        if (topics.isEmpty()) {
            log.info("知识收集: 无主题，跳过");
            return;
        }

        log.info("知识收集: 开始收集 {} 个主题", topics.size());
        int added = 0;
        for (String topic : topics) {
            try {
                String result = newsService.searchNews(5, topic);
                List<String> entries = parseResult(topic, result);
                for (String entry : entries) {
                    if (isDuplicate(entry)) continue;
                    vectorStore.add(entry);
                    added++;
                }
                Thread.sleep(500);
            } catch (Exception e) {
                log.warn("知识收集: 主题 [{}] 失败: {}", topic, e.getMessage());
            }
        }
        log.info("知识收集: 完成，新增 {} 条知识", added);
    }

    private List<String> loadTopics() {
        try {
            JsonNode root = objectMapper.readTree(
                new ClassPathResource("knowledge.json").getInputStream());
            return objectMapper.convertValue(root.get("topics"),
                new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("从 knowledge.json 加载 topics 失败", e);
            return List.of();
        }
    }

    private List<String> parseResult(String topic, String result) {
        List<String> entries = new ArrayList<>();
        if (result == null || result.startsWith("错误") || result.startsWith("没有找到")) {
            return entries;
        }
        Matcher m = ITEM_PATTERN.matcher(result);
        while (m.find()) {
            String title = m.group(1).trim();
            String url = m.group(2).trim();
            String knowledge = "【" + topic + "】" + title + " 来源: " + url;
            entries.add(knowledge);
        }
        return entries;
    }

    private boolean isDuplicate(String text) {
        float[] vec = embeddingService.embed(text);
        double maxSim = 0;
        for (int i = 0; i < vectorStore.size(); i++) {
            maxSim = Math.max(maxSim, vectorStore.similarityTo(i, vec));
        }
        return maxSim > 0.85;
    }
}
