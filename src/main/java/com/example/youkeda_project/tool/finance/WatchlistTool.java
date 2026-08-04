package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.WatchlistService;
import com.example.youkeda_project.service.assist.WatchlistService.WatchItem;
import com.example.youkeda_project.service.stock.StockService;
import com.example.youkeda_project.util.StockQuoteParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自选股管理工具：add(加入)、remove(删除)、list(查看行情)。
 */
@Component
public class WatchlistTool extends AbstractTool {

    private final WatchlistService watchlistService;
    private final StockService stockService;

    public WatchlistTool(WatchlistService watchlistService, StockService stockService) {
        this.watchlistService = watchlistService;
        this.stockService = stockService;
    }

    @Override
    public String getName() { return "watchlist"; }

    @Override
    public String getDescription() {
        return "自选股管理：operation=add 加入自选，operation=remove 删除自选，operation=list 查看自选股行情。"
            + "用户说'加自选''关注''收藏'用 add；'删自选''取消关注''移除'用 remove；'自选股''我的自选'用 list。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("operation", Map.of("type", "string",
            "enum", List.of("add", "remove", "list"), "description", "操作类型"));
        props.put("stock", Map.of("type", "string", "description", "股票名称或代码，如：贵州茅台,600519,sh600519(add/remove需要)"));
        params.put("properties", props);
        params.put("required", List.of("operation"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String op = arguments != null ? (String) arguments.get("operation") : null;
        if (op == null) return ToolResult.text("错误: 缺少操作类型(operation)，应为 add/remove/list");
        return switch (op) {
            case "add" -> add(arguments);
            case "remove" -> remove(arguments);
            case "list" -> list();
            default -> ToolResult.text("错误: 未知操作 " + op);
        };
    }

    // ==================== add: 加入自选 ====================

    private ToolResult add(Map<String, Object> arguments) {
        String stock = (String) arguments.get("stock");
        if (stock == null || stock.isBlank()) return ToolResult.text("错误: 请指定股票名称或代码");

        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        String quote = stockService.queryStock(stock);
        String code = StockQuoteParser.extractCode(quote);
        String name = StockQuoteParser.extractName(quote);
        if (code == null) return ToolResult.text("错误: 无法识别股票 '" + stock + "'\n" + quote);

        boolean added = watchlistService.add(userId, code, name != null ? name : stock);
        return added
            ? ToolResult.text("✅ 已将 " + (name != null ? name : stock) + "（" + code + "）加入自选股")
            : ToolResult.text("该股票已在自选列表中");
    }

    // ==================== remove: 删除自选 ====================

    private ToolResult remove(Map<String, Object> arguments) {
        String stock = (String) arguments.get("stock");
        if (stock == null || stock.isBlank()) return ToolResult.text("错误: 请指定股票名称或代码");

        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        String quote = stockService.queryStock(stock);
        String code = StockQuoteParser.extractCode(quote);
        String name = StockQuoteParser.extractName(quote);
        if (code == null) return ToolResult.text("错误: 无法识别股票 '" + stock + "'");

        boolean removed = watchlistService.remove(userId, code);
        return removed
            ? ToolResult.text("已从自选股中删除 " + (name != null ? name : stock))
            : ToolResult.text("该股票不在你的自选列表中");
    }

    // ==================== list: 查看自选行情 ====================

    private ToolResult list() {
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
