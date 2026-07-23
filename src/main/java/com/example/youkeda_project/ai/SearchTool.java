package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SearchTool.class);

    private final NewsService newsService;

    public SearchTool(NewsService newsService) {
        this.newsService = newsService;
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "搜索新闻、事件、知识、百科等实时信息。搜歌/查歌词请用 play_song 工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，如'徐良巡演城市'、'量子力学'、'华为最新产品'等");
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
        log.info("SearchTool 执行: num={}, keyword={}", num, keyword);
        return ToolResult.text(newsService.searchNews(num, keyword));
    }
}
