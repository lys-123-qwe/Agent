package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.stock.ExchangeRateService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExchangeRateTool extends AbstractTool {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateTool(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @Override
    public String getName() {
        return "exchange_rate";
    }

    @Override
    public String getDescription() {
        return "查询实时汇率并进行货币转换,支持任意币种之间的换算."
            + "例如:100美元等于多少人民币,EUR to CNY,日元兑人民币";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> from = new LinkedHashMap<>();
        from.put("type", "string");
        from.put("description", "源货币代码(ISO 4217 三字母代码),如 USD,CNY,EUR,JPY,GBP 等."
            + "常见:CNY(人民币) USD(美元) EUR(欧元) GBP(英镑) JPY(日元) HKD(港币)");
        properties.put("from", from);

        Map<String, Object> to = new LinkedHashMap<>();
        to.put("type", "string");
        to.put("description", "目标货币代码(ISO 4217 三字母代码),如 CNY,USD,EUR 等");
        properties.put("to", to);

        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("type", "number");
        amount.put("description", "需要转换的金额(可选,默认为 1)");
        properties.put("amount", amount);

        params.put("properties", properties);
        params.put("required", List.of("from", "to"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String from = arguments != null ? (String) arguments.get("from") : null;
        String to = arguments != null ? (String) arguments.get("to") : null;
        if (from == null || to == null) {
            return ToolResult.text("错误: 请指定源货币和目标货币,例如: 100 USD 转 CNY");
        }

        double amount = 1.0;
        if (arguments != null && arguments.get("amount") instanceof Number) {
            amount = ((Number) arguments.get("amount")).doubleValue();
        }

        from = from.trim().toUpperCase();
        to = to.trim().toUpperCase();

        String result = exchangeRateService.convert(from, to, amount);
        return ToolResult.text(result);
    }
}
