package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.assist.PositionService;
import com.example.youkeda_project.service.assist.PositionService.Position;
import com.example.youkeda_project.service.stock.StockService;
import com.example.youkeda_project.util.StockQuoteParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListPositionsTool implements Tool {

    private final PositionService positionService;
    private final StockService stockService;

    public ListPositionsTool(PositionService positionService, StockService stockService) {
        this.positionService = positionService;
        this.stockService = stockService;
    }

    @Override
    public String getName() { return "list_positions"; }

    @Override
    public String getDescription() {
        return "查看持仓列表及盈亏。用户说'持仓''我的持仓''仓位''盈亏'等时调用。不需要参数。";
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
