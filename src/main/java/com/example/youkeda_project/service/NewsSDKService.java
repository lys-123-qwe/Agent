package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NewsSDKService {

    private static final Logger log = LoggerFactory.getLogger(NewsSDKService.class);
    private static final String TIAN_API_BASE = "http://api.tianapi.com/generalnews/index";

    private final ApiConfig apiConfig;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public NewsSDKService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String getTopHeadlines(String category, int pageSize) {
        try {
            String apiKey = apiConfig.getNewsSdkApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                return "错误: 未配置天聚数行 API Key";
            }
            int num = Math.min(pageSize, 50);
            String url = TIAN_API_BASE + "?key=" + apiKey + "&num=" + num;
            log.info("天聚数行请求: {}", url.replace(apiKey, "***"));
            byte[] responseBytes = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
            return parseResponse(responseBytes, "今日新闻");
        } catch (Exception e) {
            log.error("天聚数行 top-headlines 失败", e);
            return "新闻获取失败: " + e.getMessage();
        }
    }

    public String searchNews(String keyword, int pageSize) {
        try {
            String apiKey = apiConfig.getNewsSdkApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                return "错误: 未配置天聚数行 API Key";
            }
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            int num = Math.min(pageSize, 50);
            String url = TIAN_API_BASE + "?key=" + apiKey + "&num=" + num + "&word=" + encodedKeyword;
            log.info("天聚数行搜索: {}", url.replace(apiKey, "***"));
            byte[] responseBytes = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
            return parseResponse(responseBytes, keyword);
        } catch (Exception e) {
            log.error("天聚数行 search 失败", e);
            return "新闻搜索失败: " + e.getMessage();
        }
    }

    private String parseResponse(byte[] responseBytes, String prefix) {
        try {
            String json = new String(responseBytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                return "错误: " + root.path("msg").asText("未知错误");
            }
            JsonNode newslist = root.path("newslist");
            if (!newslist.isArray() || newslist.isEmpty()) {
                return "没有找到相关新闻";
            }
            List<String[]> items = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            for (JsonNode article : newslist) {
                String title = article.path("title").asText("").trim();
                String url = article.path("url").asText("").trim();
                String source = article.path("source").asText("");
                if (title.isEmpty() || url.isEmpty()) continue;
                String cleanTitle = cleanTitle(title);
                if (cleanTitle.length() < 4) continue;
                if (!seenUrls.add(url)) continue;
                String display = source.isEmpty() ? cleanTitle : cleanTitle + " [" + source + "]";
                items.add(new String[]{display, url});
                if (items.size() >= 20) break;
            }
            if (items.isEmpty()) return "没有找到相关新闻";
            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append("：\n");
            for (int i = 0; i < items.size(); i++) {
                sb.append(i + 1).append(". ").append(items.get(i)[0])
                    .append(" [详情](").append(items.get(i)[1]).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("解析天聚数行响应失败", e);
            return "解析新闻结果失败: " + e.getMessage();
        }
    }

    private String cleanTitle(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String t = raw.trim();
        t = t.replaceAll("(?:[_＿][^_＿]+)+$", "").trim();
        t = t.replaceAll("\\s*[-—]\\s*\\S+$", "").trim();
        t = t.replaceAll("^[^|\uFF5C]+[|\uFF5C]", "").trim();
        t = t.replaceAll("^(原标题|快讯|热点|今日关注|最新消息|滚动)[：:]?", "").trim();
        t = t.replaceAll("（[^）]*图[^）]*）$", "").trim();
        t = t.replaceAll("\\([^)]*图[^)]*\\)$", "").trim();
        t = t.replaceAll("[\\[【][^\\]】]*(?:视频|音频|组图|图集|海报|直播|专题)[\\]】]$", "").trim();
        t = t.replaceAll("^[\\[【][^\\]】]*[\\]】]", "").trim();
        t = t.replaceAll("\\d+张照片$", "").trim();
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }
}
