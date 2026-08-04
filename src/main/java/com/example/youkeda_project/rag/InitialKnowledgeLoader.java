package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class InitialKnowledgeLoader {

    private static final Logger log = LoggerFactory.getLogger(InitialKnowledgeLoader.class);

    private final KnowledgeCollector knowledgeCollector;
    private final KnowledgeRepository knowledgeRepo;

    public InitialKnowledgeLoader(KnowledgeCollector knowledgeCollector,
                                  KnowledgeRepository knowledgeRepo) {
        this.knowledgeCollector = knowledgeCollector;
        this.knowledgeRepo = knowledgeRepo;
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
            Path topicsFile = Paths.get("src", "knowledge", "topics.md").toAbsolutePath().normalize();
            if (!Files.isRegularFile(topicsFile)) return List.of();
            return Files.readAllLines(topicsFile, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();
        } catch (Exception e) {
            log.warn("加载 topics 失败", e);
            return List.of();
        }
    }
}
