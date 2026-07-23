package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NewsTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(NewsTool.class);

    private final NewsService newsService;

    public NewsTool(NewsService newsService) {
        this.newsService = newsService;
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "搜索最新新闻或查询各类信息，返回格式化好的结果列表。当你需要查询实时信息、新闻或搜索特定话题时使用此工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，如'今天有什么新闻'、'AI'、'华为最新消息'、'新能源'等");
        properties.put("keyword", keyword);

        Map<String, Object> num = new LinkedHashMap<>();
        num.put("type", "integer");
        num.put("description", "返回结果条数，1-20，默认为10");
        properties.put("num", num);

        params.put("properties", properties);
        params.put("required", java.util.List.of("keyword"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        int num = 10;
        String keyword = "";
        if (arguments != null) {
            if (arguments.get("num") instanceof Integer n) {
                num = n;
            }
            if (arguments.get("keyword") instanceof String k) {
                keyword = k;
            }
        }
        log.info("NewsTool 执行: num={}, keyword={}", num, keyword);
        return ToolResult.text(newsService.searchNews(num, keyword));
    }
}
