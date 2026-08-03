package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.stock.StockService;
import com.example.youkeda_project.service.assist.WatchlistService;
import com.example.youkeda_project.util.StockQuoteParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AddToWatchlistTool implements Tool {

    private final WatchlistService watchlistService;
    private final StockService stockService;

    public AddToWatchlistTool(WatchlistService watchlistService, StockService stockService) {
        this.watchlistService = watchlistService;
        this.stockService = stockService;
    }

    @Override
    public String getName() { return "add_to_watchlist"; }

    @Override
    public String getDescription() {
        return "把股票加入自选股列表。用户说'加自选''关注''收藏股票'等时调用。参数传股票名称或代码。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stock", Map.of("type", "string", "description", "股票名称或代码，如：贵州茅台,600519,sh600519"));
        params.put("properties", props);
        params.put("required", java.util.List.of("stock"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String stock = arguments != null ? (String) arguments.get("stock") : null;
        if (stock == null || stock.isBlank()) {
            return ToolResult.text("错误: 请指定股票名称或代码");
        }

        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        String quote = stockService.queryStock(stock);
        String code = StockQuoteParser.extractCode(quote);
        String name = StockQuoteParser.extractName(quote);
        if (code == null) {
            return ToolResult.text("错误: 无法识别股票 '" + stock + "'\n" + quote);
        }

        boolean added = watchlistService.add(userId, code, name != null ? name : stock);
        if (added) {
            return ToolResult.text("✅ 已将 " + (name != null ? name : stock) + "（" + code + "）加入自选股");
        }
        return ToolResult.text("该股票已在自选列表中");
    }
}
