package com.example.youkeda_project.rag;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InitialKnowledgeLoader {

    private static final Logger log = LoggerFactory.getLogger(InitialKnowledgeLoader.class);

    private final KnowledgeCollector knowledgeCollector;
    private final KnowledgeRepository knowledgeRepo;
    private final ObjectMapper objectMapper;

    public InitialKnowledgeLoader(KnowledgeCollector knowledgeCollector,
                                  KnowledgeRepository knowledgeRepo,
                                  ObjectMapper objectMapper) {
        this.knowledgeCollector = knowledgeCollector;
        this.knowledgeRepo = knowledgeRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // 如果已有足够知识，不做初始预搜
        if (knowledgeRepo.count() >= 30) {
            log.info("初始知识加载: 知识库已有 {} 条，跳过预搜", knowledgeRepo.count());
            return;
        }
        batchCollect();
    }

    @Async
    public void batchCollect() {
        List<String> topics = loadTopics();
        if (topics.isEmpty()) return;

        log.info("初始知识加载: 开始后台预搜 {} 个主题", topics.size());
        int total = 0;
        for (String topic : topics) {
            List<String> added = knowledgeCollector.learn(topic);
            total += added.size();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("初始知识加载: 完成，共新增 {} 条知识", total);
    }

    private List<String> loadTopics() {
        try {
            JsonNode root = objectMapper.readTree(
                new ClassPathResource("knowledge.json").getInputStream());
            return objectMapper.convertValue(root.get("topics"),
                new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("加载 topics 失败", e);
            return List.of();
        }
    }
}
