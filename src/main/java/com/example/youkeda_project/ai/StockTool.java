package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ChartService;
import com.example.youkeda_project.service.FinancialDataService;
import com.example.youkeda_project.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 股票查询工具(Function Calling).
 * <p>
 * 定义 get_stock 函数,供 DeepSeek 通过 Function Calling 调用.
 * 委托 StockService 通过新浪/腾讯公开接口查询 A 股,港股,美股实时行情.
 * 定义 get_stock 函数，供 DeepSeek 通过 Function Calling 调用。
 * 支持 A 股、港股、美股的实时行情查询和 A 股历史涨幅图表生成。
 */
@Component
public class StockTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(StockTool.class);

    private final StockService stockService;
    private final ChartService chartService;
    private final FinancialDataService financialDataService;

    public StockTool(StockService stockService, ChartService chartService,
                     FinancialDataService financialDataService) {
        this.stockService = stockService;
        this.chartService = chartService;
        this.financialDataService = financialDataService;
    }

    @Override
    public String getName() {
        return "get_stock";
    }

    @Override
    public String getDescription() {
        return "查询A股,港股,美股的实时行情,包括当前价格,涨跌幅,成交量等."
            + "A股支持股票名称(如:贵州茅台,宁德时代,比亚迪)或代码(如:600519,sh600519)."
            + "港股支持名称(如:腾讯控股,阿里巴巴,美团)或代码(如:00700,hk00700)."
            + "美股支持名称(如:苹果,特斯拉,英伟达)或字母代码(如:AAPL,TSLA,NVDA)."
            + "也支持大盘指数:上证指数,深证成指,创业板指,科创50,沪深300."
            + "设置 days 参数可生成走势图(chart_type=line 折线图,chart_type=candlestick K线图).仅A股支持图表.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("type", "string");
        stock.put("description", "股票名称或代码(A股/港股/美股均支持)."
            + "A股:贵州茅台=sh600519,宁德时代=sz300750,比亚迪=sz002594,中国平安=sh601318,招商银行=sh600036."
            + "港股:腾讯控股=hk00700,阿里巴巴=hk09988,美团=hk03690,小米=hk01810,京东=hk09618."
            + "美股:苹果=gb_aapl,特斯拉=gb_tsla,英伟达=gb_nvda,微软=gb_msft,谷歌=gb_goog."
            + "指数:上证指数,深证成指,创业板指,科创50,沪深300."
            + "也支持直接输入代码如 600519,00700,AAPL,sh600519,hk00700,gb_aapl.");
        properties.put("stock", stock);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "查询最近N个交易日的历史数据并生成走势图。"
            + "7=近一周，30=近一月，90=近三月。不填或0=只查实时行情。仅A股支持图表。");
        properties.put("days", days);

        Map<String, Object> chartType = new LinkedHashMap<>();
        chartType.put("type", "string");
        chartType.put("description", "图表类型：不填=折线图（默认），line=折线图，candlestick=K线蜡烛图");
        chartType.put("enum", java.util.List.of("line", "candlestick"));
        properties.put("chart_type", chartType);

        params.put("properties", properties);
        params.put("required", java.util.List.of("stock"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String stock = arguments != null ? (String) arguments.get("stock") : null;
        if (stock == null || stock.isBlank()) {
            return ToolResult.text("错误: 请指定股票名称或代码\n"
                + "示例: 贵州茅台 / 600519 / sh600519 / 腾讯 / AAPL");
        }

        int days = 0;
        if (arguments != null && arguments.get("days") != null) {
            try {
                days = Integer.parseInt(arguments.get("days").toString());
            } catch (NumberFormatException ignored) {}
        }

        String chartType = arguments != null ? (String) arguments.get("chart_type") : null;
        if (chartType == null || chartType.isBlank()) chartType = "line";

        log.info("StockTool 执行: stock={}, days={}, chart_type={}", stock, days, chartType);

        if (days > 0) {
            return generateChart(stock, days, chartType);
        }

        String quote = stockService.queryStock(stock);
        // 取代码取基本面数据（从行情第一行提取 sh600519）
        String code = extractStockCode(quote);
        var fd = code != null ? financialDataService.fetch(code) : null;
        if (fd != null) {
            quote += financialDataService.formatSummary(stock, fd);
        }
        return ToolResult.text(quote);
    }

    private ToolResult generateChart(String stock, int days, String chartType) {
        String textResult = stockService.queryStock(stock);
        List<StockService.KlineData> history = stockService.getHistoricalKline(stock, days);

        if (history.size() < 2) {
            return ToolResult.text(textResult + "\n\n⚠️ 历史数据不足，无法生成图表");
        }

        String stockCode = extractStockCode(textResult);
        var fd = stockCode != null ? financialDataService.fetch(stockCode) : null;
        if (fd != null) {
            textResult += "\n" + financialDataService.formatSummary(stock, fd);
        }

        String displayName = stock;
        String code = stock;
        if (!textResult.isEmpty()) {
            String firstLine = textResult.split("\n")[0];
            if (firstLine.length() > 2) {
                displayName = firstLine.substring(2).trim();
            }
        }

        String period = switch (days) {
            case 7 -> "近一周";
            case 30 -> "近一月";
            case 90 -> "近三月";
            default -> "近" + days + "天";
        };

        byte[] chartBytes;
        String title;
        String subtitle;

        if ("candlestick".equals(chartType)) {
            List<ChartService.CandlestickPoint> candlePoints = new ArrayList<>();
            for (int i = 0; i < history.size(); i++) {
                StockService.KlineData k = history.get(i);
                String changePct = null;
                if (i > 0) {
                    BigDecimal prevClose = new BigDecimal(history.get(i - 1).close());
                    BigDecimal close = new BigDecimal(k.close());
                    if (prevClose.compareTo(BigDecimal.ZERO) > 0) {
                        changePct = close.subtract(prevClose)
                            .divide(prevClose, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP)
                            .toString();
                    }
                }
                long vol = 0;
                try { vol = Long.parseLong(k.volume()); } catch (NumberFormatException ignored) {}
                candlePoints.add(new ChartService.CandlestickPoint(
                    k.date(), new BigDecimal(k.open()), new BigDecimal(k.high()),
                    new BigDecimal(k.low()), new BigDecimal(k.close()), vol, changePct
                ));
            }
            title = displayName + "（" + code + "）";
            subtitle = period + "K线走势";
            chartBytes = chartService.generateCandlestickChart(title, subtitle, candlePoints);
        } else {
            List<ChartService.ChartPoint> linePoints = new ArrayList<>();
            for (int i = 0; i < history.size(); i++) {
                StockService.KlineData k = history.get(i);
                String changePct = null;
                if (i > 0) {
                    BigDecimal prevClose = new BigDecimal(history.get(i - 1).close());
                    BigDecimal close = new BigDecimal(k.close());
                    if (prevClose.compareTo(BigDecimal.ZERO) > 0) {
                        changePct = close.subtract(prevClose)
                            .divide(prevClose, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP)
                            .toString();
                    }
                }
                linePoints.add(new ChartService.ChartPoint(k.date(), new BigDecimal(k.close()), changePct));
            }
            title = displayName + "（" + code + "）" + period + "收盘价走势";
            chartBytes = chartService.generateChart(title, null, linePoints);
        }

        if (chartBytes == null) {
            return ToolResult.text(textResult + "\n\n⚠️ 图表生成失败");
        }

        String summary = buildChartSummary(displayName, code, days, history);
        return ToolResult.imageBytes(textResult + "\n\n" + summary, chartBytes);
    }

    private String extractStockCode(String quote) {
        if (quote == null) return null;
        for (String line : quote.split("\n")) {
            if (line.contains("（") && line.contains("）")) {
                int s = line.indexOf("（"), e = line.indexOf("）");
                String code = line.substring(s + 1, e);
                if (code.matches("(?i)(sh|sz)\\d{6}")) return code.toLowerCase();
            }
        }
        return null;
    }

    private String buildChartSummary(String displayName, String code, int days,
                                     List<StockService.KlineData> history) {
        String period = switch (days) {
            case 7 -> "近一周";
            case 30 -> "近一月";
            case 90 -> "近三月";
            default -> "近" + days + "天";
        };

        String firstDate = history.get(0).date();
        String lastDate = history.get(history.size() - 1).date();
        BigDecimal firstClose = new BigDecimal(history.get(0).close());
        BigDecimal lastClose = new BigDecimal(history.get(history.size() - 1).close());

        try {
            BigDecimal change = lastClose.subtract(firstClose)
                .divide(firstClose, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            String sign = change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

            return String.format("📊 %s（%s）%s收盘价走势\n%s ~ %s\n区间涨跌: %s%s%%\n最新收盘价: %s",
                displayName, code, period, firstDate, lastDate,
                sign, change.setScale(2, RoundingMode.HALF_UP), lastClose);
        } catch (NumberFormatException e) {
            return String.format("📊 %s %s走势\n%s ~ %s", displayName, period, firstDate, lastDate);
        }
    }
}
