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
public class AddPositionTool implements Tool {

    private final PositionService positionService;
    private final StockService stockService;

    public AddPositionTool(PositionService positionService, StockService stockService) {
        this.positionService = positionService;
        this.stockService = stockService;
    }

    @Override
    public String getName() { return "add_position"; }

    @Override
    public String getDescription() {
        return "记录买入股票(加仓)。用户说'买入''加仓''建仓''开仓'等时调用。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stock", Map.of("type", "string", "description", "股票名称或代码"));
        props.put("quantity", Map.of("type", "number", "description", "买入数量（股）"));
        props.put("price", Map.of("type", "number", "description", "买入价格（元/股）"));
        params.put("properties", props);
        params.put("required", java.util.List.of("stock", "quantity", "price"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String stock = arguments != null ? (String) arguments.get("stock") : null;
        double quantity = arguments != null && arguments.get("quantity") != null
            ? Double.parseDouble(arguments.get("quantity").toString()) : 0;
        double price = arguments != null && arguments.get("price") != null
            ? Double.parseDouble(arguments.get("price").toString()) : 0;

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
}
