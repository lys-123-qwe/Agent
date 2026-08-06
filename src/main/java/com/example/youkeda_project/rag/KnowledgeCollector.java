package com.example.youkeda_project.rag;

import com.example.youkeda_project.service.stock.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** 搜索网络学习新知识并写入知识库。 */


@Component
public class KnowledgeCollector {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCollector.class);
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "\\d+\\.\\s*(.*?)\\s*\\[详情]\\((https?://[^)]+)\\)");

    private final VectorStore vectorStore;
    private final KnowledgeRepository knowledgeRepo;
    private final NewsService newsService;

    public KnowledgeCollector(VectorStore vectorStore, KnowledgeRepository knowledgeRepo,
                              NewsService newsService) {
        this.vectorStore = vectorStore;
        this.knowledgeRepo = knowledgeRepo;
        this.newsService = newsService;
    }

    /** 搜索指定关键词并将结果入库，返回新增的知识文本 */
    public List<String> learn(String query) {
        try {
            String result = newsService.searchNews(5, query);
            List<String> entries = parseResult(query, result);
            List<String> newEntries = new ArrayList<>();
            for (String entry : entries) {
                if (knowledgeRepo.exists(entry)) continue;
                vectorStore.add(entry);
                newEntries.add(entry);
            }
            if (!newEntries.isEmpty()) {
                log.info("知识学习: query=\"{}\"，新增 {} 条", query, newEntries.size());
            }
            return newEntries;
        } catch (Exception e) {
            log.warn("知识学习失败: query=\"{}\", {}", query, e.getMessage());
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
            String raw = m.group(1).trim();
            String url = m.group(2).trim();
            // 截短标题/摘要，避免 prompt 过长
            String title = raw.length() > 100 ? raw.substring(0, 100) + "…" : raw;
            String knowledge = "【" + topic + "】" + title + " 来源: " + url;
            entries.add(knowledge);
        }
        return entries;
    }
}
