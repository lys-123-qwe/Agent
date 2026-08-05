package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;

import com.example.youkeda_project.service.stock.ChartService;
import com.example.youkeda_project.service.stock.StockService;
import com.example.youkeda_project.service.stock.ChartService.ChartPoint;
import com.example.youkeda_project.service.stock.ChartService.CandlestickPoint;
import com.example.youkeda_project.service.stock.KLineService.KLineData;
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
public class ChartTool extends AbstractTool {

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
            List<KLineData> klines = stockService.getHistoricalKline(stock, 60);
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

    private byte[] generateLine(String name, String code, List<KLineData> klines) throws Exception {
        List<ChartPoint> data = ChartService.toCandlestickPoints(klines, true).stream()
                .map(c -> new ChartPoint(c.date(), c.close(), c.changePct()))
                .toList();

        String subtitle = com.example.youkeda_project.util.StockCodes.marketLabel(code) + " · 近" + klines.size() + "日";
        byte[] pngBytes = chartService.generateChart(name, subtitle, data);
        saveChart(pngBytes, name + "_line"); // 留底调试
        return pngBytes;
    }

    private byte[] generateCandlestick(String name, String code, List<KLineData> klines) throws Exception {
        List<CandlestickPoint> data = ChartService.toCandlestickPoints(klines, true);
        String subtitle = com.example.youkeda_project.util.StockCodes.marketLabel(code)
            + " · 近" + klines.size() + "日 · 含MA5/10/20";
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

    /**
     * 代码解析：统一走 StockCodes.resolveSinaCode，失败抛出提示。
     */
    private String resolveStockCode(String input) {
        String code = com.example.youkeda_project.util.StockCodes.resolveSinaCode(input);
        if (code == null) {
            throw new RuntimeException("无法识别品种: " + input);
        }
        return code;
    }
}
