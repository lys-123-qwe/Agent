package com.example.youkeda_project.service;

import com.example.youkeda_project.service.ChartService.CandlestickPoint;
import com.example.youkeda_project.service.ChartService.ChartPoint;
import com.example.youkeda_project.service.ChartService.SeriesData;
import com.example.youkeda_project.service.FundService.NavData;
import com.example.youkeda_project.service.FuturesService.KlineData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金融分析服务 - 智能体核心。
 * <p>
 * 并行聚合股票、期货、基金数据，为 AI 提供分析上下文。
 * 支持单品深度分析和多品对比分析。
 */
@Service
public class FinancialAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FinancialAnalysisService.class);

    private final StockService stockService;
    private final FuturesService futuresService;
    private final FundService fundService;
    private final ChartService chartService;

    public FinancialAnalysisService(StockService stockService, FuturesService futuresService,
                                     FundService fundService, ChartService chartService) {
        this.stockService = stockService;
        this.futuresService = futuresService;
        this.fundService = fundService;
        this.chartService = chartService;
    }

    /**
     * 分析单只股票/期货/基金。
     */
    public AnalysisResult analyze(String symbol, String type, int days) {
        log.info("金融分析: symbol={}, type={}, days={}", symbol, type, days);
        AnalysisResult result = new AnalysisResult();
        result.symbol = symbol;
        result.type = type;

        // 1. 获取实时行情
        String realtime = switch (type) {
            case "stock" -> stockService.queryStock(symbol);
            case "futures" -> futuresService.queryFutures(symbol);
            case "fund" -> fundService.queryFund(symbol);
            default -> "不支持的类型: " + type;
        };
        result.realtimeData = realtime;

        // 2. 获取K线数据并生成图表
        if (days > 0) {
            try {
                byte[] chart = switch (type) {
                    case "stock" -> generateStockChart(symbol, days);
                    case "futures" -> generateFuturesChart(symbol, days);
                    case "fund" -> generateFundChart(symbol, days);
                    default -> null;
                };
                result.chartBytes = chart;
            } catch (Exception e) {
                log.warn("图表生成失败: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * 多品对比分析。
     */
    public ComparisonResult compare(List<Map<String, String>> symbols, int days) {
        log.info("金融对比分析: symbols={}, days={}", symbols, days);
        ComparisonResult result = new ComparisonResult();
        result.items = new ArrayList<>();

        for (Map<String, String> item : symbols) {
            String symbol = item.get("symbol");
            String type = item.get("type");
            AnalysisResult analysis = analyze(symbol, type, days);
            result.items.add(analysis);
        }

        // 生成对比图
        if (days > 0 && result.items.size() >= 2) {
            try {
                result.comparisonChart = generateComparisonChart(result.items, days);
            } catch (Exception e) {
                log.warn("对比图生成失败: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * 构建金融分析的 System Prompt。
     */
    public String buildSystemPrompt() {
        return """
            你是一位资深金融分析师，擅长股票、期货、基金的综合分析。

            你的分析风格：
            1. 数据驱动：基于实时行情和历史K线数据
            2. 技术分析：结合均线（MA5/MA10/MA20/MA60）、成交量、趋势形态
            3. 基本面参考：结合行业地位、估值水平
            4. 风险提示：明确指出潜在风险和注意事项

            输出格式要求：
            - 使用 Markdown 表格展示关键数据
            - 技术指标用加粗标注
            - 给出明确的操作建议（买入/持有/卖出/观望）
            - 每条分析不超过3句话
            - 必须包含风险提示

            分析维度：
            ━━ 价格走势 ━━
            当前价格、涨跌幅、成交量变化

            ━━ 技术分析 ━━
            均线系统（多头/空头排列）、MACD、KDJ、支撑位/压力位

            ━━ 趋势判断 ━━
            短期（1-5天）、中期（1-4周）、长期（1-3月）趋势

            ━━ 操作建议 ━━
            明确的方向性建议 + 仓位控制建议

            ━━ 风险提示 ━━
            需要关注的风险因素
            """;
    }

    // ==================== 图表生成 ====================

    private byte[] generateStockChart(String symbol, int days) {
        List<StockService.KlineData> klineList = stockService.getHistoricalKline(symbol, days);
        if (klineList == null || klineList.isEmpty()) return null;

        List<CandlestickPoint> points = new ArrayList<>();
        for (StockService.KlineData k : klineList) {
            points.add(new CandlestickPoint(
                k.date(),
                new BigDecimal(k.open()),
                new BigDecimal(k.high()),
                new BigDecimal(k.low()),
                new BigDecimal(k.close()),
                Long.parseLong(k.volume()),
                null
            ));
        }

        String title = symbol + " K线图";
        String subtitle = "日K线 · " + days + "个交易日";
        return chartService.generateCandlestickChart(title, subtitle, points);
    }

    private byte[] generateFuturesChart(String symbol, int days) {
        List<KlineData> klineList = futuresService.getHistoricalKline(symbol, days);
        if (klineList == null || klineList.isEmpty()) return null;

        List<CandlestickPoint> points = new ArrayList<>();
        for (KlineData k : klineList) {
            points.add(new CandlestickPoint(
                k.date(),
                new BigDecimal(k.open()),
                new BigDecimal(k.high()),
                new BigDecimal(k.low()),
                new BigDecimal(k.close()),
                Long.parseLong(k.volume()),
                null
            ));
        }

        String title = symbol + " 期货K线图";
        String subtitle = "日K线 · " + days + "个交易日";
        return chartService.generateCandlestickChart(title, subtitle, points);
    }

    private byte[] generateFundChart(String symbol, int days) {
        List<NavData> navList = fundService.getHistoricalNav(symbol, days);
        if (navList == null || navList.isEmpty()) return null;

        List<ChartPoint> points = new ArrayList<>();
        for (NavData nav : navList) {
            points.add(new ChartPoint(nav.date(), new BigDecimal(nav.nav()), nav.changePct()));
        }

        String title = symbol + " 净值走势";
        String subtitle = "日净值 · " + days + "个交易日";
        return chartService.generateChart(title, subtitle, points);
    }

    private byte[] generateComparisonChart(List<AnalysisResult> items, int days) {
        List<SeriesData> seriesList = new ArrayList<>();

        for (AnalysisResult item : items) {
            List<ChartPoint> points = new ArrayList<>();
            try {
                switch (item.type) {
                    case "stock" -> {
                        List<StockService.KlineData> klineList = stockService.getHistoricalKline(item.symbol, days);
                        if (klineList != null) {
                            for (StockService.KlineData k : klineList) {
                                points.add(new ChartPoint(k.date(), new BigDecimal(k.close()), null));
                            }
                        }
                    }
                    case "futures" -> {
                        List<KlineData> klineList = futuresService.getHistoricalKline(item.symbol, days);
                        if (klineList != null) {
                            for (KlineData k : klineList) {
                                points.add(new ChartPoint(k.date(), new BigDecimal(k.close()), null));
                            }
                        }
                    }
                    case "fund" -> {
                        List<NavData> navList = fundService.getHistoricalNav(item.symbol, days);
                        if (navList != null) {
                            for (NavData nav : navList) {
                                points.add(new ChartPoint(nav.date(), new BigDecimal(nav.nav()), null));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取 {} 数据失败: {}", item.symbol, e.getMessage());
            }
            if (!points.isEmpty()) {
                seriesList.add(new SeriesData(item.symbol, points));
            }
        }

        if (seriesList.size() < 2) return null;
        return chartService.generateComparisonChart("多品对比", days + "个交易日涨跌对比", seriesList);
    }

    /**
     * 分析结果数据包。
     */
    public static class AnalysisResult {
        public String symbol;
        public String type;
        public String realtimeData;
        public byte[] chartBytes;

        public String toContextString() {
            StringBuilder sb = new StringBuilder();
            sb.append("品种: ").append(symbol).append(" (").append(type).append(")\n");
            if (realtimeData != null) sb.append("实时行情:\n").append(realtimeData).append("\n");
            return sb.toString();
        }
    }

    /**
     * 对比分析结果数据包。
     */
    public static class ComparisonResult {
        public List<AnalysisResult> items;
        public byte[] comparisonChart;

        public String toContextString() {
            StringBuilder sb = new StringBuilder();
            sb.append("对比品种:\n");
            for (AnalysisResult item : items) {
                sb.append("---\n").append(item.toContextString()).append("\n");
            }
            return sb.toString();
        }
    }
}
