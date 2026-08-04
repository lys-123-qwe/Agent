package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;

import com.example.youkeda_project.service.chart.ChartService;
import com.example.youkeda_project.service.stock.FundService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基金对比工具（Function Calling）。
 * <p>
 * 定义 compare_funds 函数，供 DeepSeek 通过 Function Calling 调用。
 * 支持多只基金的涨幅对比图表生成。
 */
@Component
public class CompareFundsTool extends AbstractTool {

        private final FundService fundService;
    private final ChartService chartService;

    public CompareFundsTool(FundService fundService, ChartService chartService) {
        this.fundService = fundService;
        this.chartService = chartService;
    }

    @Override
    public String getName() {
        return "compare_funds";
    }

    @Override
    public String getDescription() {
        return "对比多只基金的涨幅走势。"
            + "支持ETF和开放式基金，最多可对比5只基金。"
            + "生成归一化涨跌幅对比折线图。"
            + "示例: 对比沪深300ETF、中证500ETF、创业板ETF近90天走势。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> funds = new LinkedHashMap<>();
        funds.put("type", "string");
        funds.put("description", "基金名称或代码，多个用逗号分隔。"
            + "最多5只。示例: '沪深300ETF,中证500ETF,创业板ETF' 或 '510300,510500,159915'");
        properties.put("funds", funds);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "对比最近N天的涨幅。7=近一周，30=近一月，90=近三月。默认30天。");
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", List.of("funds"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String fundsStr = arguments != null ? (String) arguments.get("funds") : null;
        if (fundsStr == null || fundsStr.isBlank()) {
            return ToolResult.text("错误: 请指定至少2只基金\n"
                + "示例: compare_funds(funds='沪深300ETF,中证500ETF,创业板ETF', days=30)");
        }

        int days = 30;
        if (arguments != null && arguments.get("days") != null) {
            try {
                days = Integer.parseInt(arguments.get("days").toString());
            } catch (NumberFormatException ignored) {}
        }

        String[] fundArr = fundsStr.split("[,，]");
        List<String> fundList = new ArrayList<>();
        for (String f : fundArr) {
            String trimmed = f.trim();
            if (!trimmed.isEmpty()) fundList.add(trimmed);
        }

        if (fundList.size() < 2) {
            return ToolResult.text("错误: 请指定至少2只基金进行对比");
        }
        if (fundList.size() > 5) {
            return ToolResult.text("错误: 最多对比5只基金");
        }

        log.info("CompareFundsTool 执行: funds={}, days={}", fundList, days);

        List<ChartService.SeriesData> seriesList = new ArrayList<>();
        StringBuilder infoBuilder = new StringBuilder();
        infoBuilder.append("📊 基金涨幅对比（").append(getPeriodLabel(days)).append("）\n\n");

        for (String fund : fundList) {
            String textResult = fundService.queryFund(fund);
            List<FundService.NavData> history = fundService.getHistoricalNav(fund, days);

            if (history.size() < 2) {
                infoBuilder.append("⚠️ ").append(fund).append(": 历史数据不足\n");
                continue;
            }

            String displayName = extractDisplayName(textResult, fund);

            List<ChartService.ChartPoint> points = new ArrayList<>();
            for (FundService.NavData nav : history) {
                points.add(new ChartService.ChartPoint(nav.date(), new BigDecimal(nav.nav()), nav.changePct()));
            }

            seriesList.add(new ChartService.SeriesData(displayName, points));

            // 计算涨跌幅
            BigDecimal first = new BigDecimal(history.get(0).nav());
            BigDecimal last = new BigDecimal(history.get(history.size() - 1).nav());
            BigDecimal change = last.subtract(first)
                .divide(first, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            String sign = change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            infoBuilder.append("• ").append(displayName).append(": ")
                .append(sign).append(change.setScale(2, java.math.RoundingMode.HALF_UP)).append("%\n");
        }

        if (seriesList.size() < 2) {
            return ToolResult.text(infoBuilder.toString() + "\n⚠️ 有效基金不足2只，无法生成对比图");
        }

        String title = "基金涨幅对比";
        String subtitle = getPeriodLabel(days) + "涨跌幅走势";

        byte[] chartBytes = chartService.generateComparisonChart(title, subtitle, seriesList);
        if (chartBytes == null) {
            return ToolResult.text(infoBuilder + "\n⚠️ 图表生成失败");
        }

        return ToolResult.imageBytes(infoBuilder.toString(), chartBytes);
    }

    private String extractDisplayName(String textResult, String fund) {
        String firstLine = textResult.split("\n")[0];
        if (firstLine.length() > 2) {
            String name = firstLine.substring(2).trim();
            int parenIdx = name.indexOf("（");
            if (parenIdx > 0) return name.substring(0, parenIdx);
            return name;
        }
        return fund;
    }

    private String getPeriodLabel(int days) {
        return switch (days) {
            case 7 -> "近一周";
            case 30 -> "近一月";
            case 90 -> "近三月";
            default -> "近" + days + "天";
        };
    }
}
