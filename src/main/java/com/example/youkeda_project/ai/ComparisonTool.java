package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ChartService;
import com.example.youkeda_project.service.FinancialDataService;
import com.example.youkeda_project.service.KLineService;
import com.example.youkeda_project.service.StockService;
import com.example.youkeda_project.service.TechnicalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.*;

@Component
public class ComparisonTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ComparisonTool.class);

    private final StockService stockService;
    private final KLineService kLineService;
    private final TechnicalService technicalService;
    private final ChartService chartService;
    private final FinancialDataService financialDataService;

    public ComparisonTool(StockService stockService, KLineService kLineService,
                          TechnicalService technicalService, ChartService chartService,
                          FinancialDataService financialDataService) {
        this.stockService = stockService;
        this.kLineService = kLineService;
        this.technicalService = technicalService;
        this.chartService = chartService;
        this.financialDataService = financialDataService;
    }

    @Override
    public String getName() {
        return "compare_stocks";
    }

    @Override
    public String getDescription() {
        return "多只A股对比分析，包含实时行情对比、技术指标（MA/RSI）、异常检测和技术面综合评分。"
            + "支持板块联动分析（白酒/银行/新能源等）。"
            + "传入多个股票名称或代码（逗号分隔），如：贵州茅台,五粮液 或 sh600519,sz000858";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> symbols = new LinkedHashMap<>();
        symbols.put("type", "string");
        symbols.put("description", "股票名称或代码，多个用逗号分隔。"
            + "如：贵州茅台,五粮液,泸州老窖");
        properties.put("symbols", symbols);

        params.put("properties", properties);
        params.put("required", List.of("symbols"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String symbols = arguments != null ? (String) arguments.get("symbols") : null;
        if (symbols == null || symbols.isBlank()) {
            return ToolResult.text("错误: 请指定股票名称或代码\n示例: 贵州茅台,五粮液,泸州老窖");
        }

        String[] items = symbols.split("[,，]");
        List<StockAnalysis> results = new ArrayList<>();

        for (String item : items) {
            item = item.trim();
            if (item.isEmpty()) continue;
            try {
                String quote = stockService.queryStock(item);
                String code = extractCode(quote);
                String name = extractName(quote);
                String symbol = item;

                List<KLineService.KLineData> history = kLineService.fetchDailyKLine(item, 60);
                if (history == null || history.size() < 5) {
                    results.add(new StockAnalysis(name, code, symbol, quote, null, null, null));
                    continue;
                }

                // 获取一年历史数据用于分位计算
                List<KLineService.KLineData> yearHistory = kLineService.fetchDailyKLine(item, 250);

                var maList = technicalService.calcMA(history, List.of(5, 10, 20, 60));
                var rsiList = technicalService.calcRSI(history, List.of(6, 12, 24));
                AnomalyResult anomaly = detectAnomaly(history);
                int score = calcScore(history, maList, rsiList, anomaly);
                double percentile = calcPercentile(yearHistory);

                results.add(new StockAnalysis(name, code, symbol, quote, maList, rsiList, anomaly, score, percentile));
            } catch (Exception e) {
                log.warn("对比分析失败: {}", e.getMessage());
            }
        }

        String report = formatReport(results);
        byte[] chartImage = generateOverlayChart(results);
        if (chartImage != null) {
            return ToolResult.imageBytes(report, chartImage);
        }
        return ToolResult.text(report);
    }

    private double calcPercentile(List<KLineService.KLineData> history) {
        if (history == null || history.size() < 10) return -1;
        double current = history.get(history.size() - 1).getClose();
        double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (var h : history) {
            if (h.getClose() < min) min = h.getClose();
            if (h.getClose() > max) max = h.getClose();
        }
        if (max - min < 0.01) return 50;
        return (current - min) / (max - min) * 100;
    }

    private byte[] generateOverlayChart(List<StockAnalysis> results) {
        List<StockAnalysis> valid = results.stream().filter(r -> r.maList != null).toList();
        if (valid.size() < 2) return null;
        Color[] colors = {new Color(0xE53935), new Color(0x2196F3), new Color(0x4CAF50), new Color(0xFF9800)};
        List<ChartService.OverlaySeries> series = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            var r = valid.get(i);
            List<KLineService.KLineData> history = kLineService.fetchDailyKLine(r.symbol, 60);
            if (history == null || history.size() < 2) continue;
            List<ChartService.ChartPoint> pts = new ArrayList<>();
            for (var h : history) {
                pts.add(new ChartService.ChartPoint(h.getDay(), BigDecimal.valueOf(h.getClose()), null));
            }
            String label = r.name.length() > 6 ? r.name.substring(0, 6) : r.name;
            series.add(new ChartService.OverlaySeries(label, colors[i % colors.length], pts));
        }
        if (series.size() < 2) return null;
        return chartService.generateOverlayChart("走势叠加对比（归一化）", series);
    }

    private String extractCode(String quote) {
        if (quote == null || !quote.contains("（")) return "";
        int s = quote.indexOf("（"), e = quote.indexOf("）");
        return s > 0 && e > s ? quote.substring(s + 1, e) : "";
    }

    private String extractName(String quote) {
        if (quote == null) return "";
        String[] lines = quote.split("\n");
        String first = lines[0].trim();
        if (first.startsWith("📈") || first.startsWith("📉") || first.startsWith("📊")) {
            return first.substring(2).trim();
        }
        return first;
    }

    private AnomalyResult detectAnomaly(List<KLineService.KLineData> history) {
        if (history == null || history.size() < 5) return null;
        var latest = history.get(history.size() - 1);

        // 涨跌幅异常检测
        double[] dailyChanges = new double[history.size() - 1];
        for (int i = 1; i < history.size(); i++) {
            dailyChanges[i - 1] = (history.get(i).getClose() - history.get(i - 1).getClose())
                / history.get(i - 1).getClose() * 100;
        }
        double mean = avg(dailyChanges);
        double std = std(dailyChanges, mean);
        double todayChange = dailyChanges[dailyChanges.length - 1];
        boolean priceAnomaly = std > 0 && Math.abs(todayChange - mean) > 2 * std;

        // 成交量异常检测
        double[] volumes = history.stream().mapToDouble(KLineService.KLineData::getVolume).toArray();
        double volMean = avg(volumes);
        double volStd = std(volumes, volMean);
        boolean volAnomaly = volStd > 0 && (volumes[volumes.length - 1] - volMean) > 2 * volStd;

        return new AnomalyResult(todayChange, mean, std, volumes[volumes.length - 1], volMean, priceAnomaly, volAnomaly);
    }

    private int calcScore(List<KLineService.KLineData> history,
                          List<TechnicalService.MaResult> maList,
                          List<TechnicalService.RsiResult> rsiList,
                          AnomalyResult anomaly) {
        int score = 5;

        var latest = history.get(history.size() - 1);
        double close = latest.getClose();

        // MA 多头排列：MA5 > MA10 > MA20
        Map<Integer, Double> maMap = new HashMap<>();
        for (var ma : maList) maMap.put(ma.getPeriod(), ma.getValue());
        if (maMap.containsKey(5) && maMap.containsKey(10) && maMap.containsKey(20)) {
            if (maMap.get(5) > maMap.get(10) && maMap.get(10) > maMap.get(20)) score += 2;
            else if (maMap.get(5) < maMap.get(10) && maMap.get(10) < maMap.get(20)) score -= 2;
        }

        // 价格站在 MA20 上方
        if (maMap.containsKey(20) && close > maMap.get(20)) score += 1;
        else score -= 1;

        // RSI 正常区间
        for (var rsi : rsiList) {
            if (rsi.getValue() > 70 || rsi.getValue() < 30) score -= 1;
            else score += 1;
        }

        // 放量（如果是涨）
        if (anomaly != null) {
            if (anomaly.volAnomaly() && anomaly.vol() > anomaly.volMean() * 1.5) score += 1;
        }

        return Math.max(0, Math.min(10, score));
    }

    private String formatReport(List<StockAnalysis> results) {
        if (results.isEmpty()) return "错误: 所有股票均无法获取数据";

        StringBuilder sb = new StringBuilder("📊 股票对比分析\n\n");

        // 表头
        sb.append(String.format("%-12s %-10s %-8s %-8s %-8s %-8s %-8s %-5s\n",
            "名称", "代码", "当前价", "涨跌幅", "PE", "MA评分", "异常", "总分"));
        sb.append("-".repeat(75)).append("\n");

        for (var r : results) {
            String name = r.name.length() > 6 ? r.name.substring(0, 6) : r.name;
            String price = extractField(r.quote, "当前价");
            String change = extractField(r.quote, "涨跌幅");
            String pe = extractField(r.quote, "市盈率");
            String maStr = r.maList == null || r.maList.isEmpty() ? "N/A" :
                String.format("%.0f/%.0f", r.maList.get(0).getValue(), r.maList.get(r.maList.size() - 1).getValue());
            String anomalyStr = "—";
            if (r.anomaly != null) {
                if (r.anomaly.priceAnomaly()) anomalyStr = "⚠涨跌异动";
                else if (r.anomaly.volAnomaly()) anomalyStr = "⚠放量";
            }
            String scoreStr = r.score >= 0 ? r.score + "/10" : "N/A";

            sb.append(String.format("%-12s %-10s %-8s %-8s %-8s %-8s %-8s %-5s\n",
                name, r.code, price, change, pe, maStr, anomalyStr, scoreStr));
        }

        // 基本面对比
        sb.append("\n**基本面对比**\n");
        for (var r : results) {
            var fd = financialDataService.fetch(r.code);
            if (fd != null) {
                sb.append(financialDataService.formatSummary(r.name, fd)).append("\n");
            }
        }

        // 评分规则说明
        sb.append("\n**评分规则（满分10分）**\n");
        sb.append("· MA多头排列（MA5>MA10>MA20）+2分，空头排列 -2分\n");
        sb.append("· 股价站上MA20 +1分，跌破 -1分\n");
        sb.append("· RSI在30-70正常区间 +1分/项，超买超卖 -1分/项\n");
        sb.append("· 放量上涨 +1分\n\n");

        // PE/PB 历史分位
        sb.append("\n**近一年价格分位**\n");
        for (var r : results) {
            if (r.percentile < 0) continue;
            String tip;
            if (r.percentile <= 20) tip = "⚠低位区间";
            else if (r.percentile >= 80) tip = "⚠高位区间";
            else tip = "中性区间";
            sb.append(String.format("· %s：当前价处于近一年 %.0f%% 分位（%s）\n", r.name, r.percentile, tip));
        }

        sb.append("\n**技术面评分详情**\n");
        for (var r : results) {
            if (r.maList == null) continue;
            sb.append("\n").append(r.name).append("（").append(r.code).append("）\n");

            // MA 值
            for (var ma : r.maList) {
                sb.append(String.format("MA%d=%.2f  ", ma.getPeriod(), ma.getValue()));
            }
            sb.append("\n");

            // RSI 值
            if (r.rsiList != null) {
                for (var rsi : r.rsiList) {
                    String tip = rsi.getValue() > 70 ? "⚠超买" : rsi.getValue() < 30 ? "⚠超卖" : "";
                    sb.append(String.format("RSI%d=%.1f%s  ", rsi.getPeriod(), rsi.getValue(), tip));
                }
                sb.append("\n");
            }

            // 评分
            sb.append("综合评分：").append(r.score).append("/10 — ");
            if (r.score >= 8) sb.append("✅ 强势，趋势健康");
            else if (r.score >= 5) sb.append("⚖️ 中性，观望");
            else sb.append("❌ 偏弱，注意风险");
            sb.append("\n");

            // 异常提示
            if (r.anomaly != null && (r.anomaly.priceAnomaly() || r.anomaly.volAnomaly())) {
                sb.append("⚠️ **异常检测**");
                if (r.anomaly.priceAnomaly()) {
                    sb.append(String.format("：今日涨跌 %.2f%%，远超近期均值 %.2f%%±%.2f",
                        r.anomaly.todayChange(), r.anomaly.mean(), r.anomaly.std()));
                }
                if (r.anomaly.volAnomaly()) {
                    sb.append(String.format("；成交量 %.0f，远超均值 %.0f",
                        r.anomaly.vol(), r.anomaly.volMean()));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String extractField(String quote, String field) {
        if (quote == null) return "—";
        for (String line : quote.split("\n")) {
            if (line.contains(field)) {
                String val = line.substring(line.indexOf(":") + 1).trim();
                return val.length() > 10 ? val.substring(0, 10) : val;
            }
        }
        return "—";
    }

    private double avg(double[] arr) {
        return Arrays.stream(arr).average().orElse(0);
    }

    private double std(double[] arr, double mean) {
        return Math.sqrt(Arrays.stream(arr).map(v -> Math.pow(v - mean, 2)).average().orElse(0));
    }

    private record AnomalyResult(double todayChange, double mean, double std,
                                 double vol, double volMean,
                                 boolean priceAnomaly, boolean volAnomaly) {}

    private record StockAnalysis(String name, String code, String symbol, String quote,
                                   List<TechnicalService.MaResult> maList,
                                   List<TechnicalService.RsiResult> rsiList,
                                   AnomalyResult anomaly, int score, double percentile) {
        StockAnalysis(String name, String code, String symbol, String quote,
                      List<TechnicalService.MaResult> maList,
                      List<TechnicalService.RsiResult> rsiList,
                      AnomalyResult anomaly, int score) {
            this(name, code, symbol, quote, maList, rsiList, anomaly, score, -1);
        }
        StockAnalysis(String name, String code, String symbol, String quote,
                      List<TechnicalService.MaResult> maList,
                      List<TechnicalService.RsiResult> rsiList,
                      AnomalyResult anomaly) {
            this(name, code, symbol, quote, maList, rsiList, anomaly, -1, -1);
        }
    }
}
