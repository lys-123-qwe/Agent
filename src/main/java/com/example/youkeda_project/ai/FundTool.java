package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ChartService;
import com.example.youkeda_project.service.FundService;
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
 * 基金查询工具（Function Calling）。
 * <p>
 * 定义 get_fund 函数，供 DeepSeek 通过 Function Calling 调用。
 * 支持 ETF（场内基金）和开放式基金的实时/净值查询，以及历史涨幅图表生成。
 */
@Component
public class FundTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FundTool.class);

    private final FundService fundService;
    private final ChartService chartService;

    public FundTool(FundService fundService, ChartService chartService) {
        this.fundService = fundService;
        this.chartService = chartService;
    }

    @Override
    public String getName() {
        return "get_fund";
    }

    @Override
    public String getDescription() {
        return "查询基金的实时行情、最新净值或历史走势图。"
            + "ETF（场内基金）: 沪深300ETF(sh510300)、上证50ETF(sh510050)、创业板ETF(sz159915)、"
            + "芯片ETF(sz159995)、新能源ETF(sh516160)、医药ETF(sh512010)、白酒ETF(sz159869)、"
            + "军工ETF(sh512660)、证券ETF(sh512880)、黄金ETF(sh518880)等。"
            + "开放式基金: 易方达蓝筹精选(005827)、中欧医疗健康(003095)、招商中证白酒(161725)、"
            + "景顺长城新兴成长(260108)、富国天惠成长(161005)、兴全合润(163406)、"
            + "天弘沪深300(000961)、余额宝(000198)等。"
            + "支持中文名称（沪深300ETF/易方达蓝筹精选）或代码（510300/005827）。"
            + "设置 days 参数可生成走势图（如 7天/30天/90天）。基金只有净值数据，仅支持折线图。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> fund = new LinkedHashMap<>();
        fund.put("type", "string");
        fund.put("description", "基金名称或代码。"
            + "ETF: 沪深300ETF=sh510300、上证50ETF=sh510050、创业板ETF=sz159915、"
            + "芯片ETF=sz159995、新能源ETF=sh516160、医药ETF=sh512010、白酒ETF=sz159869。"
            + "开放式基金: 易方达蓝筹精选=005827、中欧医疗健康=003095、招商中证白酒=161725。"
            + "也可直接输入代码如 510300、005827。");
        properties.put("fund", fund);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "查询最近N天的历史净值并生成走势图。"
            + "7=近一周，30=近一月，90=近三月。不填或0=只查实时行情。");
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", java.util.List.of("fund"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String fund = arguments != null ? (String) arguments.get("fund") : null;
        if (fund == null || fund.isBlank()) {
            return ToolResult.text("错误: 请指定基金名称或代码\n"
                + "示例: 沪深300ETF / 易方达蓝筹精选 / 510300 / 005827");
        }

        // 解析 days 参数
        int days = 0;
        if (arguments != null && arguments.get("days") != null) {
            try {
                days = Integer.parseInt(arguments.get("days").toString());
            } catch (NumberFormatException ignored) {}
        }

        log.info("FundTool 执行: fund={}, days={}", fund, days);

        // 生成图表模式
        if (days > 0) {
            return generateChart(fund, days);
        }

        // 普通查询模式
        return ToolResult.text(fundService.queryFund(fund));
    }

    private ToolResult generateChart(String fund, int days) {
        String textResult = fundService.queryFund(fund);
        List<FundService.NavData> history = fundService.getHistoricalNav(fund, days);

        if (history.size() < 2) {
            return ToolResult.text(textResult + "\n\n⚠️ 历史数据不足，无法生成图表");
        }

        // 解析基金名称和代码
        String displayName = fund;
        String code = fund;
        if (history.size() > 0) {
            String firstLine = textResult.split("\n")[0];
            if (firstLine.length() > 2) {
                displayName = firstLine.substring(2).trim();
            }
        }

        // 转换为通用 ChartPoint
        List<ChartService.ChartPoint> points = new ArrayList<>();
        for (FundService.NavData nav : history) {
            points.add(new ChartService.ChartPoint(nav.date(), new BigDecimal(nav.nav()), nav.changePct()));
        }

        String period = getPeriodLabel(days);
        String title = displayName + "（" + code + "）";
        String subtitle = period + "净值走势";

        byte[] chartBytes = chartService.generateChart(title, subtitle, points);
        if (chartBytes == null) {
            return ToolResult.text(textResult + "\n\n⚠️ 图表生成失败");
        }

        String summary = buildChartSummary(fund, days, history);
        return ToolResult.imageBytes(summary, chartBytes);
    }

    private String getPeriodLabel(int days) {
        return switch (days) {
            case 7 -> "近一周";
            case 30 -> "近一月";
            case 90 -> "近三月";
            default -> "近" + days + "天";
        };
    }

    private String buildChartSummary(String fund, int days, List<FundService.NavData> history) {
        String period = switch (days) {
            case 7 -> "近一周";
            case 30 -> "近一月";
            case 90 -> "近三月";
            default -> "近" + days + "天";
        };

        String firstNav = history.get(0).nav();
        String lastNav = history.get(history.size() - 1).nav();
        String firstDate = history.get(0).date();
        String lastDate = history.get(history.size() - 1).date();

        try {
            java.math.BigDecimal first = new java.math.BigDecimal(firstNav);
            java.math.BigDecimal last = new java.math.BigDecimal(lastNav);
            java.math.BigDecimal change = last.subtract(first)
                .divide(first, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new java.math.BigDecimal("100"));
            String sign = change.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "+" : "";

            return String.format("📊 %s（%s）%s涨幅图\n%s ~ %s\n区间涨幅: %s%s%%\n最新净值: %s",
                fund, fund, period, firstDate, lastDate,
                sign, change.setScale(2, java.math.RoundingMode.HALF_UP), lastNav);
        } catch (NumberFormatException e) {
            return String.format("📊 %s %s涨幅图\n%s ~ %s",
                fund, period, firstDate, lastDate);
        }
    }
}
