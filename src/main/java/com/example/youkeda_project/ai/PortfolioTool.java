package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.PortfolioService;
import com.example.youkeda_project.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PortfolioTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(PortfolioTool.class);

    private final PortfolioService portfolioService;
    private final StockService stockService;

    private static final String SYS_USER_ID = "o9cq80yrqPo3V3Tm7Y2rXU1vmlXg@im.wechat";

    public PortfolioTool(PortfolioService portfolioService, StockService stockService) {
        this.portfolioService = portfolioService;
        this.stockService = stockService;
    }

    @Override
    public String getName() {
        return "portfolio";
    }

    @Override
    public String getDescription() {
        return "股票持仓管理。支持记录买入（add）、清仓（remove）、查询盈亏（query）。"
            + "如：'记录我买了100股茅台成本1200'（调add），'清仓茅台'（调remove），'我的持仓赚了多少'（调query）。"
            + "code和shares和cost为必填。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("description", "操作类型：add=记录买入/加仓, remove=清仓, query=查询持仓盈亏");
        action.put("enum", java.util.List.of("add", "remove", "query"));
        properties.put("action", action);

        Map<String, Object> code = new LinkedHashMap<>();
        code.put("type", "string");
        code.put("description", "股票名称或代码，如：贵州茅台、sh600519。query时无需传");
        properties.put("code", code);

        Map<String, Object> shares = new LinkedHashMap<>();
        shares.put("type", "number");
        shares.put("description", "买入股数，如100。add时必填");
        properties.put("shares", shares);

        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("type", "number");
        cost.put("description", "买入单价（每股价格），如1200。add时必填");
        properties.put("cost", cost);

        params.put("properties", properties);
        params.put("required", java.util.List.of("action"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String action = arguments != null ? (String) arguments.get("action") : null;
        if (action == null) {
            return ToolResult.text("请指定操作类型：add=记录买入, remove=清仓, query=查询持仓");
        }

        switch (action) {
            case "query": {
                String result = portfolioService.queryPortfolio(SYS_USER_ID);
                return ToolResult.text(result);
            }
            case "add": {
                String code = arguments.get("code") instanceof String s ? s.trim() : null;
                if (code == null || code.isBlank()) {
                    return ToolResult.text("请提供股票名称或代码");
                }
                double shares = 0;
                if (arguments.get("shares") instanceof Number n) {
                    shares = n.doubleValue();
                }
                if (shares <= 0) {
                    return ToolResult.text("请提供有效的买入股数");
                }
                double costPrice = 0;
                if (arguments.get("cost") instanceof Number n) {
                    costPrice = n.doubleValue();
                }
                if (costPrice <= 0) {
                    return ToolResult.text("请提供有效的买入单价");
                }
                String displayName = code;
                String[] lines = stockService.queryStock(code).split("\n");
                if (lines.length > 0) {
                    String first = lines[0];
                    if (first.contains("（") && first.contains("）")) {
                        displayName = first.substring(0, first.indexOf("（"));
                    } else if (!first.startsWith("错误:")) {
                        displayName = first.replaceAll("（.*）", "").trim();
                    }
                }
                portfolioService.addPosition(SYS_USER_ID, code, displayName, shares, costPrice);
                return ToolResult.text("已记录: " + displayName + " " + formatNum(shares) + "股，成本价" + formatPrice(costPrice) + "元");
            }
            case "remove": {
                String code = arguments.get("code") instanceof String s ? s.trim() : null;
                if (code == null || code.isBlank()) {
                    return ToolResult.text("请提供要清仓的股票名称或代码");
                }
                portfolioService.removePosition(SYS_USER_ID, code);
                return ToolResult.text("已清仓 " + code);
            }
            default:
                return ToolResult.text("不支持的操作: " + action);
        }
    }

    private String formatNum(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v);
    }

    private String formatPrice(double v) {
        return String.format("%.2f", v);
    }
}
