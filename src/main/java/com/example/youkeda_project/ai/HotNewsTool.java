package com.example.youkeda_project.ai;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HotNewsTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(HotNewsTool.class);
    private static final String API_URL = "https://api.bochaai.com/v1/web-search";

    private final ApiConfig apiConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HotNewsTool(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String getName() {
        return "get_hot_news";
    }

    @Override
    public String getDescription() {
        return "获取今日热点新闻，支持按类别筛选（国内、国际、娱乐、体育、科技、财经等），返回标题和简介";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("description", "新闻类别，如：国内、国际、娱乐、体育、科技、财经、社会。不传则获取综合热点");
        properties.put("category", category);

        Map<String, Object> count = new LinkedHashMap<>();
        count.put("type", "integer");
        count.put("description", "返回条数，默认10，最大20");
        properties.put("count", count);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String apiKey = apiConfig.getBochaApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return ToolResult.text("错误: 未配置博查 API Key，无法获取热点新闻");
        }

        String category = arguments != null ? (String) arguments.get("category") : null;
        int count = 10;
        if (arguments != null && arguments.get("count") instanceof Number n) {
            count = Math.min(n.intValue(), 20);
        }

        String query = category != null && !category.isBlank()
            ? "今日" + category + "热点新闻"
            : "今日热点新闻";

        log.info("HotNewsTool 执行: query={}, count={}", query, count);

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("query", query);
            requestBody.put("freshness", "oneDay");
            requestBody.put("summary", true);
            requestBody.put("count", count);

            String bodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String responseJson = response.body();
            log.info("博查响应: {}", responseJson);

            JsonNode root = objectMapper.readTree(responseJson);

            JsonNode results = root.path("data").path("webPages").path("value");
            if (!results.isArray() || results.isEmpty()) {
                return ToolResult.text("暂无热点新闻数据");
            }

            StringBuilder sb = new StringBuilder();
            String title = category != null && !category.isBlank() ? category + "热点新闻" : "今日热点新闻";
            sb.append("=== ").append(title).append(" ===\n\n");

            int idx = 1;
            for (JsonNode item : results) {
                String itemTitle = item.path("title").asText();
                String itemSummary = item.path("summary").asText();
                String itemUrl = item.path("url").asText();
                String siteName = item.path("siteName").asText();

                sb.append(idx).append(". ").append(itemTitle).append("\n");
                if (!itemSummary.isBlank()) {
                    sb.append("   ").append(itemSummary).append("\n");
                }
                sb.append("   [来源: ").append(siteName).append("](").append(itemUrl).append(")\n\n");
                idx++;
            }

            return ToolResult.text(sb.toString());
        } catch (Exception e) {
            log.error("博查 API 调用失败", e);
            return ToolResult.text("获取热点新闻失败: " + e.getMessage());
        }
    }
}
