package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FinancialAnalysisService;
import com.example.youkeda_project.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 金融分析工具(Function Calling).
 *
 * 供 DeepSeek 调用 analyze_stock,基于技术指标预测股票/指数方向,
 * 置信度,有效期,预计底部/顶部区域.
 */
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
        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("type", "string");
        stock.put("description", "股票或指数名称/代码,如:贵州茅台,600519,sh600519,腾讯,AAPL,上证指数.");
        properties.put("stock", stock);

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

        // 先查询实时价格
        String stockInfo = stockService.queryStock(stock);
        double currentPrice = parsePrice(stockInfo);
        if (Double.isNaN(currentPrice)) {
            return ToolResult.text("错误: 无法获取 " + stock + " 的实时价格,请检查名称/代码是否正确.\n原始行情:\n" + stockInfo);
        }

        FinancialAnalysisService.AnalysisResult result = analysisService.analyze(stock, currentPrice);
        return ToolResult.text(result.summary());
    }

    /**
     * 从 StockService.queryStock 返回的格式化字符串中解析当前价格.
     */
    private double parsePrice(String stockInfo) {
        if (stockInfo == null || stockInfo.isBlank()) return Double.NaN;
        try {
            // 尝试匹配 "当前价格: 123.45" 或 "当前价: 123.45" 或 "Price: 123.45"
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:当前价格|当前价|Price)[:\uff1a]\\s*([0-9]+(?:\\.[0-9]+)?)");
            java.util.regex.Matcher m = p.matcher(stockInfo);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
            // 备用:直接找第一个数字
            p = java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");
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
