package com.example.youkeda_project.ai;

import com.example.youkeda_project.config.ApiConfig;
import com.example.youkeda_project.service.NewsService;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SearchTool.class);

    private final NewsService newsService;
    private final ApiConfig apiConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SearchTool(NewsService newsService, ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.newsService = newsService;
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "搜索实时信息。不传 keyword 则获取今日新闻头条；传 keyword 进行通用网页搜索（百科、官网、资讯等）；"
            + "传 keyword + category 则搜索指定分类新闻（国内、国际、娱乐、体育、科技、财经、社会）。"
            + "搜歌/查歌词请用 play_song 工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，如'徐良巡演城市'、'量子力学原理'、'如何做红烧肉'等。不传则获取今日新闻头条");
        properties.put("keyword", keyword);

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("description", "新闻类别（仅在需要特定类别新闻时传入）：国内、国际、娱乐、体育、科技、财经、社会");
        properties.put("category", category);

        Map<String, Object> count = new LinkedHashMap<>();
        count.put("type", "integer");
        count.put("description", "返回条数，1-20，默认10");
        properties.put("count", count);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        int num = 10;
        String keyword = null;
        String category = null;

        if (arguments != null) {
            if (arguments.get("count") instanceof Number n) {
                num = Math.min(Math.max(n.intValue(), 1), 20);
            }
            if (arguments.get("keyword") instanceof String k && !k.isBlank()) {
                keyword = k;
            }
            if (arguments.get("category") instanceof String c && !c.isBlank()) {
                category = c;
            }
        }

        // 无关键词 → 今日新闻头条
        if (keyword == null || keyword.isBlank()) {
            log.info("SearchTool: 获取今日新闻头条");
            return ToolResult.text(newsService.searchOfficialNews());
        }

        // 有关键词 + 分类 → 按分类搜索新闻（走 NewsService 天聚+博查）
        if (category != null && !category.isBlank()) {
            log.info("SearchTool: 新闻分类搜索, keyword={}, category={}, num={}", keyword, category, num);
            return ToolResult.text(newsService.searchNews(num, keyword, category));
        }

        // 有关键词（无分类）→ 通用网页搜索（直调博查，不局限于新闻）
        log.info("SearchTool: 通用搜索, keyword={}, num={}", keyword, num);
        return ToolResult.text(bochaGeneralSearch(keyword, num));
    }

    private String bochaGeneralSearch(String keyword, int maxItems) {
        String apiKey = apiConfig.getBochaApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置搜索 API Key";
        }
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("query", keyword);
            params.put("summary", true);
            params.put("count", Math.min(maxItems * 2, 50));

            String bodyJson = objectMapper.writeValueAsString(params);
            log.info("博查通用搜索请求: {}", bodyJson);

            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(apiConfig.getNewsBochaSearchUrl()))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<byte[]> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofByteArray());
            String responseJson = new String(hresp.body(), StandardCharsets.UTF_8);
            log.info("博查通用搜索响应: {}", responseJson);

            JsonNode root = objectMapper.readTree(responseJson);
            int code = root.path("code").asInt();
            if (code != 200) {
                return "搜索失败: " + root.path("msg").asText("未知错误");
            }

            JsonNode value = root.path("data").path("webPages").path("value");
            if (!value.isArray() || value.isEmpty()) {
                return "没有找到相关结果";
            }

            List<String[]> items = new java.util.ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();

            for (JsonNode item : value) {
                String title = item.path("name").asText("").trim();
                String url = item.path("url").asText("").trim();
                String snippet = item.path("snippet").asText("");

                if (title.isEmpty() || url.isEmpty()) continue;
                if (!seenUrls.add(url)) continue;

                String display = title;
                if (!snippet.isBlank()) {
                    String s = snippet.replaceAll("\\s+", " ").trim();
                    if (s.length() > 80) s = s.substring(0, 80) + "...";
                    display = title + " — " + s;
                }
                items.add(new String[]{display, url});
                if (items.size() >= maxItems) break;
            }

            if (items.isEmpty()) {
                return "没有找到相关结果";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("「").append(keyword).append("」搜索结果：\n");
            for (int i = 0; i < items.size(); i++) {
                sb.append(i + 1).append(". ").append(items.get(i)[0])
                        .append(" [详情](").append(items.get(i)[1]).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("博查通用搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }
}
