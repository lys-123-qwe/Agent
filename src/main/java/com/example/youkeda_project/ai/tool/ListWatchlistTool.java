package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.stock.StockService;
import com.example.youkeda_project.service.assist.WatchlistService;
import com.example.youkeda_project.service.assist.WatchlistService.WatchItem;
import com.example.youkeda_project.util.StockQuoteParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListWatchlistTool implements Tool {

    private final WatchlistService watchlistService;
    private final StockService stockService;

    public ListWatchlistTool(WatchlistService watchlistService, StockService stockService) {
        this.watchlistService = watchlistService;
        this.stockService = stockService;
    }

    @Override
    public String getName() { return "list_watchlist"; }

    @Override
    public String getDescription() {
        return "查看自选股列表及实时行情。用户说'自选股''我的自选''持仓''关注的股票'等时调用。不需要参数。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<String, Object>());
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        List<WatchItem> items = watchlistService.list(userId);
        if (items.isEmpty()) {
            return ToolResult.text("你的自选股列表为空\n可以在聊天中说'把茅台加入自选'来添加");
        }

        StringBuilder sb = new StringBuilder("📋 我的自选股\n\n");
        int fail = 0;
        for (WatchItem item : items) {
            try {
                String quote = stockService.queryStock(item.code());
                String price = StockQuoteParser.extractField(quote, "当前价");
                String change = StockQuoteParser.extractField(quote, "涨跌幅");
                sb.append(String.format("· %s（%s）%s  %s\n", item.name(), item.code(),
                    price != null ? price : "—", change != null ? change : ""));
            } catch (Exception e) {
                fail++;
            }
        }
        sb.append("\n共 ").append(items.size()).append(" 只股票");
        if (fail > 0) sb.append("，").append(fail).append(" 只查询失败");
        return ToolResult.text(sb.toString());
    }
}
