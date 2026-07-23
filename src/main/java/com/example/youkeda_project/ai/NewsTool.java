package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
        return "get_daily_news";
    }

    @Override
    public String getDescription() {
        return "获取今日最新新闻头条，返回格式化好的新闻列表。用户想看新闻、今天发生了什么、今日热点时用此工具。无需参数，直接调用。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return null;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        log.info("NewsTool 执行: 获取今日新闻");
        return ToolResult.text(newsService.searchOfficialNews());
    }
}
