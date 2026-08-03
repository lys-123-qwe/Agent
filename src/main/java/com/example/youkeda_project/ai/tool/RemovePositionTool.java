package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.assist.PositionService;
import com.example.youkeda_project.service.stock.StockService;
import com.example.youkeda_project.util.StockQuoteParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RemovePositionTool implements Tool {

    private final PositionService positionService;
    private final StockService stockService;

    public RemovePositionTool(PositionService positionService, StockService stockService) {
        this.positionService = positionService;
        this.stockService = stockService;
    }

    @Override
    public String getName() { return "remove_position"; }

    @Override
    public String getDescription() {
        return "记录卖出股票(减仓/清仓)。用户说'卖出''减仓''清仓''平仓'等时调用。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stock", Map.of("type", "string", "description", "股票名称或代码"));
        props.put("quantity", Map.of("type", "number", "description", "卖出数量（股），不传则全部卖出"));
        params.put("properties", props);
        params.put("required", java.util.List.of("stock"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String stock = arguments != null ? (String) arguments.get("stock") : null;
        double quantity = arguments != null && arguments.get("quantity") != null
            ? Double.parseDouble(arguments.get("quantity").toString()) : -1;

        if (stock == null) {
            return ToolResult.text("错误: 请指定股票名称或代码");
        }

        String userId = AIService.getCurrentUserId();
        if (userId == null) return ToolResult.text("错误: 无法获取用户信息");

        String quote = stockService.queryStock(stock);
        String code = StockQuoteParser.extractCode(quote);
        String name = StockQuoteParser.extractName(quote);
        if (code == null) return ToolResult.text("错误: 无法识别股票 '" + stock + "'");

        if (quantity < 0) {
            var positions = positionService.list(userId);
            for (var p : positions) {
                if (p.code().equals(code)) {
                    quantity = p.quantity();
                    break;
                }
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
}
