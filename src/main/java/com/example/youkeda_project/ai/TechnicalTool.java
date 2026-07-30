package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FinancialDataService;
import com.example.youkeda_project.service.KLineService;
import com.example.youkeda_project.service.TechnicalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TechnicalTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TechnicalTool.class);

    private final KLineService kLineService;
    private final TechnicalService technicalService;
    private final FinancialDataService financialDataService;

    public TechnicalTool(KLineService kLineService, TechnicalService technicalService,
                         FinancialDataService financialDataService) {
        this.kLineService = kLineService;
        this.technicalService = technicalService;
        this.financialDataService = financialDataService;
    }

    @Override
    public String getName() {
        return "stock_technical";
    }

    @Override
    public String getDescription() {
        return "对A股和国内期货进行技术分析，计算MA（移动平均线）和RSI（相对强弱指标），"
            + "支持多标的对比（用逗号分隔）。传入股票名称或代码即可。"
            + "如果用户问：分析走势、技术面、MA、RSI、均线、超买超卖，必须调用此工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> symbols = new LinkedHashMap<>();
        symbols.put("type", "string");
        symbols.put("description", "股票名称或代码。支持多个用逗号分隔。"
            + "A股：贵州茅台/sh600519/600519，"
            + "期货：沪金/nf_AU0、螺纹钢/nf_RB0。");
        properties.put("symbols", symbols);

        Map<String, Object> maPeriods = new LinkedHashMap<>();
        maPeriods.put("type", "string");
        maPeriods.put("description", "MA周期，用逗号分隔。如 5,10,20,60。默认5,10,20,60");
        properties.put("ma_periods", maPeriods);

        Map<String, Object> rsiPeriods = new LinkedHashMap<>();
        rsiPeriods.put("type", "string");
        rsiPeriods.put("description", "RSI周期，用逗号分隔。如 6,12,24。默认6,12,24");
        properties.put("rsi_periods", rsiPeriods);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "获取最近N个交易日的数据（默认60，最大500）");
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", List.of("symbols"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String symbols = arguments != null ? (String) arguments.get("symbols") : null;
        if (symbols == null || symbols.isBlank()) {
            return ToolResult.text("错误: 请指定股票名称或代码\n示例: 贵州茅台 / 贵州茅台,五粮液");
        }

        String maStr = arguments != null ? (String) arguments.get("ma_periods") : null;
        String rsiStr = arguments != null ? (String) arguments.get("rsi_periods") : null;
        int days = 60;
        if (arguments != null && arguments.get("days") != null) {
            try { days = Integer.parseInt(arguments.get("days").toString()); } catch (NumberFormatException ignored) {}
        }

        List<Integer> maPeriods = parsePeriods(maStr, List.of(5, 10, 20, 60));
        List<Integer> rsiPeriods = parsePeriods(rsiStr, List.of(6, 12, 24));

        String[] items = symbols.split("[,，]");
        StringBuilder sb = new StringBuilder("📊 技术分析报告\n\n");

        for (String item : items) {
            item = item.trim();
            if (item.isEmpty()) continue;
            try {
                List<KLineService.KLineData> data;
                if (item.startsWith("nf_") || item.matches("(?i).*(沪金|螺纹钢|铁矿石|沪铜|原油|甲醇|纯碱|豆粕|棕榈油|生猪).*")) {
                    data = kLineService.fetchFuturesDailyKLine(item, days);
                } else {
                    data = kLineService.fetchDailyKLine(item, days);
                }

                if (data == null || data.size() < 2) {
                    sb.append("❌ ").append(item).append("：数据不足或无法识别\n\n");
                    continue;
                }

                sb.append("**").append(item).append("**\n");
                sb.append(technicalService.formatAnalysis(data, maPeriods, rsiPeriods)).append("\n");
            } catch (Exception e) {
                log.warn("技术分析失败: {}", e.getMessage());
                sb.append("❌ ").append(item).append("：分析失败\n\n");
            }
        }

        // 补充第一个标的的基本面
        String first = items[0];
        var fd = financialDataService.fetch(first);
        if (fd != null) {
            sb.append("\n\n📋 基本面参考\n").append(financialDataService.formatOneLine(first, fd));
        }

        return ToolResult.text(sb.toString());
    }

    private List<Integer> parsePeriods(String str, List<Integer> defaults) {
        if (str == null || str.isBlank()) return defaults;
        try {
            return List.of(str.split("[,，]")).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
        } catch (NumberFormatException e) {
            return defaults;
        }
    }
}
