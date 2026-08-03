package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ChartService;
import com.example.youkeda_project.service.StockService;
import com.example.youkeda_project.service.ChartService.ChartPoint;
import com.example.youkeda_project.service.ChartService.CandlestickPoint;
import com.example.youkeda_project.service.StockService.KlineData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图表生成工具（Function Calling）。
 * <p>
 * 定义 generate_chart 函数，供 DeepSeek 调用。
 * 从 StockService 拉取历史K线数据，委托 ChartService 渲染为 PNG。
 * <p>
 * 触发条件：用户明确说"画图"、"K线图"、"走势图"、"图表"等。
 */
@Component
public class ChartTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ChartTool.class);

    private static final String CHART_DIR = "data/charts";

    private final ChartService chartService;
    private final StockService stockService;

    public ChartTool(ChartService chartService, StockService stockService) {
        this.chartService = chartService;
        this.stockService = stockService;
        // 确保图表目录存在
        try {
            Files.createDirectories(Paths.get(CHART_DIR));
        } catch (Exception ignored) {}
    }

    @Override
    public String getName() {
        return "generate_chart";
    }

    @Override
    public String getDescription() {
        return "生成股票/指数近期走势折线图或K线图（PNG图片）。"
            + "当用户说「画图」「K线图」「走势图」「图表」「来张图」时调用此工具。"
            + "支持 line（折线图）和 candlestick（K线图）两种类型。"
            + "仅支持A股/港股/美股个股和指数，不支持期货和基金。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("type", "string");
        stock.put("description", "股票或指数名称/代码，如：贵州茅台、sh600519、宁德时代、上证指数。");
        properties.put("stock", stock);

        Map<String, Object> chartType = new LinkedHashMap<>();
        chartType.put("type", "string");
        chartType.put("description", "图表类型：line（折线图，默认）、candlestick（K线蜡烛图）。");
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
            return ToolResult.text("错误: 请指定要画图的股票或指数");
        }

        String chartType = arguments != null ? (String) arguments.get("chart_type") : "line";
        if (chartType == null) chartType = "line";

        log.info("ChartTool 执行: stock={}, type={}", stock, chartType);

        try {
            // 1. 解析代码
            String code = resolveStockCode(stock);
            String name = stock;

            // 2. 拉取历史K线数据（60天）
            List<KlineData> klines = stockService.getHistoricalKline(stock, 60);
            if (klines.size() < 3) {
                return ToolResult.text("抱歉，「" + name + "」历史数据不足，无法生成图表。请确认品种代码正确。");
            }

            // 3. 生成图表 PNG 字节
            byte[] pngBytes;
            String chartLabel;
            if ("candlestick".equals(chartType)) {
                pngBytes = generateCandlestick(name, code, klines);
                chartLabel = "K线图";
            } else {
                pngBytes = generateLine(name, code, klines);
                chartLabel = "近期走势图";
            }

            return ToolResult.imageBytes("✅ 已生成「" + name + "」" + chartLabel, pngBytes);

        } catch (Exception e) {
            log.error("图表生成失败: {}", e.getMessage(), e);
            return ToolResult.text("抱歉，图表生成失败: " + e.getMessage()
                    + "\n请确认品种代码正确，或稍后重试。");
        }
    }

    // ==================== 图表类型生成 ====================

    private byte[] generateLine(String name, String code, List<KlineData> klines) throws Exception {
        List<ChartPoint> data = new ArrayList<>();
        for (KlineData k : klines) {
            BigDecimal close = new BigDecimal(k.close());
            String changePct = null;
            if (!data.isEmpty()) {
                BigDecimal prevClose = data.getLast().value();
                if (prevClose.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pct = close.subtract(prevClose)
                            .divide(prevClose, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    changePct = pct.toString();
                }
            }
            data.add(new ChartPoint(k.date(), close, changePct));
        }

        String subtitle = getMarketLabel(code) + " · 近" + klines.size() + "日";
        byte[] pngBytes = chartService.generateChart(name, subtitle, data);
        saveChart(pngBytes, name + "_line"); // 留底调试
        return pngBytes;
    }

    private byte[] generateCandlestick(String name, String code, List<KlineData> klines) throws Exception {
        List<CandlestickPoint> data = new ArrayList<>();
        for (KlineData k : klines) {
            BigDecimal open = new BigDecimal(k.open());
            BigDecimal high = new BigDecimal(k.high());
            BigDecimal low = new BigDecimal(k.low());
            BigDecimal close = new BigDecimal(k.close());
            long volume = parseLong(k.volume());

            String changePct = null;
            if (!data.isEmpty()) {
                BigDecimal prevClose = data.getLast().close();
                if (prevClose.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pct = close.subtract(prevClose)
                            .divide(prevClose, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    changePct = pct.toString();
                }
            }
            data.add(new CandlestickPoint(k.date(), open, high, low, close, volume, changePct));
        }

        String subtitle = getMarketLabel(code) + " · 近" + klines.size() + "日 · 含MA5/10/20";
        byte[] pngBytes = chartService.generateCandlestickChart(name, subtitle, data);
        saveChart(pngBytes, name + "_kline"); // 留底调试
        return pngBytes;
    }

    // ==================== 保存 ====================

    private String saveChart(byte[] pngBytes, String name) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = name + "_" + timestamp + ".png";
        Path filePath = Paths.get(CHART_DIR, fileName);
        Files.write(filePath, pngBytes);
        log.info("图表已保存: {} ({} bytes)", filePath.toAbsolutePath(), pngBytes.length);
        return filePath.toAbsolutePath().toUri().toString();
    }

    // ==================== 工具方法 ====================

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }

    private String getMarketLabel(String code) {
        if (code.startsWith("sh")) return "上证";
        if (code.startsWith("sz")) return "深证";
        if (code.startsWith("bj")) return "北交所";
        if (code.startsWith("hk")) return "港股";
        if (code.startsWith("gb_")) return "美股";
        return "A股";
    }

    /**
     * 简易代码解析。
     */
    private String resolveStockCode(String input) {
        Map<String, String> quickMap = Map.ofEntries(
                Map.entry("上证指数", "sh000001"),
                Map.entry("深证成指", "sz399001"),
                Map.entry("创业板指", "sz399006"),
                Map.entry("科创50", "sh000688"),
                Map.entry("沪深300", "sh000300"),
                Map.entry("贵州茅台", "sh600519"),
                Map.entry("宁德时代", "sz300750"),
                Map.entry("比亚迪", "sz002594"),
                Map.entry("中国平安", "sh601318"),
                Map.entry("招商银行", "sh600036"),
                Map.entry("五粮液", "sz000858"),
                Map.entry("隆基绿能", "sh601012"),
                Map.entry("中芯国际", "sh688981"),
                Map.entry("腾讯控股", "hk00700"),
                Map.entry("阿里巴巴", "hk09988"),
                Map.entry("美团", "hk03690"),
                Map.entry("苹果", "gb_aapl"),
                Map.entry("特斯拉", "gb_tsla"),
                Map.entry("英伟达", "gb_nvda")
        );

        String mapped = quickMap.get(input);
        if (mapped != null) return mapped;

        if (input.matches("(?i)^(?:sh|sz|bj|hk|gb_)[a-z0-9.]+$")) {
            return input.toLowerCase();
        }
        if (input.matches("^\\d{5,6}$")) {
            char first = input.charAt(0);
            return (first == '6' || first == '5' ? "sh" : "sz") + input;
        }
        if (input.matches("(?i)^[a-z]+$")) {
            return "gb_" + input.toLowerCase();
        }
        throw new RuntimeException("无法识别品种: " + input);
    }
}