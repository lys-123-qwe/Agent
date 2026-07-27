package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一搜索工具，合并了原 HotNewsTool、NewsTool、SearchTool 的职责。
 * <p>
 * 三种模式：
 * <ol>
 *   <li>不传 keyword → 今日新闻头条（原 NewsTool）</li>
 *   <li>传 keyword → 按关键词搜索（原 SearchTool）</li>
 *   <li>传 keyword + category → 按分类搜索热点（原 HotNewsTool）</li>
 * </ol>
 * 所有搜索统一走 NewsService（天聚数行 → 博查降级 + 统一过滤），
 * 避免直接调博查绕过过滤逻辑。
 */
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
        return "搜索新闻和实时信息。不传 keyword 则获取今日新闻头条；传 keyword 则搜索指定内容；"
            + "可指定 category 筛选分类（国内、国际、娱乐、体育、科技、财经、社会等）。"
            + "搜歌/查歌词请用 play_song 工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，如'徐良巡演城市'、'量子力学'等。不传则获取今日新闻头条");
        properties.put("keyword", keyword);

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("description", "新闻类别，如：国内、国际、娱乐、体育、科技、财经、社会。仅在需要特定类别新闻时传入");
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

        // 有关键词 → 搜索
        if (category != null && !category.isBlank()) {
            log.info("SearchTool: keyword={}, category={}, num={}", keyword, category, num);
            return ToolResult.text(newsService.searchNews(num, keyword, category));
        }

        log.info("SearchTool: keyword={}, num={}", keyword, num);
        return ToolResult.text(newsService.searchNews(num, keyword));
    }
}
