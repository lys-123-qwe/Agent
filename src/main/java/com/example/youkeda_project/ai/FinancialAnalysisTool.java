package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FinancialAnalysisService;
import com.example.youkeda_project.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FinancialAnalysisTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FinancialAnalysisTool.class);

    private final FinancialAnalysisService analysisService;
    private final StockService stockService;

    public FinancialAnalysisTool(FinancialAnalysisService analysisService, StockService stockService) {
        this.analysisService = analysisService;
        this.stockService = stockService;
    }

    @Override
    public String getName() {
        return "analyze_stock";
    }

    @Override
    public String getDescription() {
        return "基于 MA/RSI/MACD/布林带/ATR 等技术指标,预测股票或指数的走势方向,置信度,预测有效期和预计底部/顶部区域." +
            "支持 A 股,港股,美股和主要指数." +
            "当用户问股票走势,怎么看,未来方向,什么时候触底/见顶,技术面分析等,必须调用此工具.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("stock", Map.of(
            "type", "string",
            "description", "股票或指数名称/代码,如:贵州茅台,600519,sh600519,腾讯,AAPL,上证指数."
        ));
        params.put("properties", properties);
        params.put("required", java.util.List.of("stock"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String stock = arguments != null ? (String) arguments.get("stock") : null;
        if (stock == null || stock.isBlank()) {
            return ToolResult.text("错误: 请指定股票或指数名称/代码");
        }
        log.info("FinancialAnalysisTool 执行: stock={}", stock);

        String stockInfo = stockService.queryStock(stock);
        double currentPrice = parsePrice(stockInfo);
        if (Double.isNaN(currentPrice)) {
            return ToolResult.text("错误: 无法获取 " + stock + " 的实时价格,请检查名称/代码是否正确.\n原始行情:\n" + stockInfo);
        }

        FinancialAnalysisService.AnalysisResult result = analysisService.analyze(stock, currentPrice);
        return ToolResult.text(result.summary());
    }

    private double parsePrice(String stockInfo) {
        if (stockInfo == null || stockInfo.isBlank()) return Double.NaN;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:\\*\\*)?(?:当前价格|当前价|Price)(?:\\*\\*)?\\s*(?::|\uff1a|\\|)\\s*([0-9]+(?:\\.[0-9]+)?)");
            java.util.regex.Matcher m = p.matcher(stockInfo);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
            p = java.util.regex.Pattern.compile("([6-9]\\.[0-9]+|[1-9][0-9]+\\.[0-9]+)");
            m = p.matcher(stockInfo);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Exception e) {
            log.warn("解析价格失败: {}", stockInfo, e);
        }
        return Double.NaN;
    }
}
