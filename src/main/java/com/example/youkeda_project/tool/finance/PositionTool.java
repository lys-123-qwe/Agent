package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.PositionService;
import com.example.youkeda_project.service.assist.PositionService.Position;
import com.example.youkeda_project.service.stock.StockService;
import com.example.youkeda_project.util.StockQuoteParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持仓管理工具：add(买入/加仓)、remove(卖出/减仓/清仓)、list(查看持仓与盈亏)。
 */

@Component
public class PositionTool extends AbstractTool {

    private final PositionService positionService;
    private final StockService stockService;

    public PositionTool(PositionService positionService, StockService stockService) {
        this.positionService = positionService;
        this.stockService = stockService;
    }

    @Override
    public String getName() { return "position"; }

    @Override
    public String getDescription() {
        return "持仓管理：operation=add 记录买入/加仓，operation=remove 记录卖出/减仓/清仓，operation=list 查看持仓与盈亏。"
            + "用户说'买入''加仓''建仓'用 add；'卖出''减仓''清仓''平仓'用 remove；'持仓''我的持仓''仓位''盈亏'用 list。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("operation", Map.of("type", "string",
            "enum", List.of("add", "remove", "list"), "description", "操作类型"));
        props.put("stock", Map.of("type", "string", "description", "股票名称或代码(add/remove需要)"));
        props.put("quantity", Map.of("type", "number", "description", "数量（股），remove不传则全部卖出"));
        props.put("price", Map.of("type", "number", "description", "价格（元/股），add需要"));
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

    // ==================== add: 买入/加仓 ====================

    /** 安全解析数值参数，失败返回默认值（避免模型传非数字导致整轮对话崩溃）。 */
    private static double parseDouble(Object v, double def) {
        if (v == null) return def;
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return def; }
    }

    private ToolResult add(Map<String, Object> arguments) {
        String stock = (String) arguments.get("stock");
        double quantity = parseDouble(arguments.get("quantity"), 0);
        double price = parseDouble(arguments.get("price"), 0);

        if (stock == null || quantity <= 0 || price <= 0) {
            return ToolResult.text("错误: 参数不正确，需要指定股票、数量（股）和买入价格");
        }

        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        String quote = stockService.queryStock(stock);
        String code = StockQuoteParser.extractCode(quote);
        String name = StockQuoteParser.extractName(quote);
        if (code == null) return ToolResult.text("错误: 无法识别股票 '" + stock + "'");

        double cost = quantity * price;
        String costStr = cost >= 1e8 ? String.format("%.2f亿", cost / 1e8)
            : cost >= 1e4 ? String.format("%.2f万", cost / 1e4) : String.format("%.2f", cost);

        positionService.addOrUpdate(userId, code, name != null ? name : stock, quantity, price);
        return ToolResult.text(String.format("✅ 买入 %s（%s）%.0f股，均价 %.2f元，总成本 %s",
            name != null ? name : stock, code, quantity, price, costStr));
    }

    // ==================== remove: 卖出/减仓/清仓 ====================

    private ToolResult remove(Map<String, Object> arguments) {
        String stock = (String) arguments.get("stock");
        double quantity = parseDouble(arguments.get("quantity"), -1);

        if (stock == null) return ToolResult.text("错误: 请指定股票名称或代码");

        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        String quote = stockService.queryStock(stock);
        String code = StockQuoteParser.extractCode(quote);
        String name = StockQuoteParser.extractName(quote);
        if (code == null) return ToolResult.text("错误: 无法识别股票 '" + stock + "'");

        if (quantity < 0) {
            var positions = positionService.list(userId);
            for (var p : positions) {
                if (p.code().equals(code)) { quantity = p.quantity(); break; }
            }
            if (quantity <= 0) return ToolResult.text("该股票不在你的持仓中");
        }

        boolean ok = positionService.reduce(userId, code, quantity);
        if (ok) {
            String qtyStr = quantity >= 10000 ? String.format("%.2f万股", quantity / 10000)
                : String.format("%.0f股", quantity);
            return ToolResult.text("✅ 卖出 " + (name != null ? name : stock) + "（" + code + "）" + qtyStr);
        }
        return ToolResult.text("卖出失败，请检查持仓数量是否足够");
    }

    // ==================== list: 查看持仓与盈亏 ====================

    private ToolResult list() {
        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        List<Position> items = positionService.list(userId);
        if (items.isEmpty()) {
            return ToolResult.text("你的持仓为空\n可以在聊天中说'买入100股茅台,价格1350'来添加持仓");
        }

        StringBuilder sb = new StringBuilder("📊 我的持仓\n\n");
        double totalPnl = 0;
        for (Position item : items) {
            try {
                String quote = stockService.queryStock(item.code());
                String priceStr = StockQuoteParser.extractField(quote, "当前价");
                if (priceStr == null) continue;
                double currentPrice = Double.parseDouble(priceStr.replace(",", ""));

                double costValue = item.quantity() * item.buyPrice();
                double marketValue = item.quantity() * currentPrice;
                double pnl = marketValue - costValue;
                double pnlPct = costValue > 0 ? (pnl / costValue) * 100 : 0;
                totalPnl += pnl;

                String pnlStr = pnl >= 0 ? "+" + String.format("%.2f", pnl) : String.format("%.2f", pnl);
                String pnlPctStr = pnlPct >= 0 ? "+" + String.format("%.1f", pnlPct) + "%" : String.format("%.1f", pnlPct) + "%";

                sb.append(String.format("· %s（%s）\n", item.name(), item.code()));
                sb.append(String.format("  持仓: %.0f股 成本: %.2f 现价: %s 盈亏: %s（%s）\n",
                    item.quantity(), item.buyPrice(), priceStr, pnlStr, pnlPctStr));
            } catch (Exception e) {
                sb.append(String.format("· %s（%s）查询失败\n", item.name(), item.code()));
            }
        }

        String totalStr = totalPnl >= 0 ? "+" + String.format("%.2f", totalPnl) : String.format("%.2f", totalPnl);
        sb.append("\n总盈亏: ").append(totalStr);
        return ToolResult.text(sb.toString());
    }
}
