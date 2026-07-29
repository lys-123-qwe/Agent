package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ChartService;
import com.example.youkeda_project.service.FuturesService;
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
 * 期货查询工具（Function Calling）。
 * <p>
 * 定义 get_futures 函数，供 DeepSeek 通过 Function Calling 调用。
 * 支持国际/国内期货的实时行情查询和历史涨幅图表生成。
 */
@Component
public class FuturesTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FuturesTool.class);

    private final FuturesService futuresService;
    private final ChartService chartService;

    public FuturesTool(FuturesService futuresService, ChartService chartService) {
        this.futuresService = futuresService;
        this.chartService = chartService;
    }

    @Override
    public String getName() {
        return "get_futures";
    }

    @Override
    public String getDescription() {
        return "查询国际/国内期货的实时行情或历史涨幅图表。"
            + "国际期货: 原油(hf_CL)、黄金(hf_GC)、白银(hf_SI)、铜(hf_HG)、天然气(hf_NG)、"
            + "玉米(hf_ZC)、小麦(hf_ZW)、大豆(hf_ZS)、棉花(hf_CT)、糖(hf_SB)、铂金(hf_PL)、钯金(hf_PA)。"
            + "国内期货: 螺纹钢(nf_RB0)、热卷(nf_HC0)、铁矿石(nf_I0)、焦炭(nf_J0)、焦煤(nf_JM0)、"
            + "沪铜(nf_CU0)、沪铝(nf_AL0)、沪锌(nf_ZN0)、沪镍(nf_NI0)、不锈钢(nf_SS0)、"
            + "沪金(nf_AU0)、沪银(nf_AG0)、"
            + "原油(nf_SC0)、甲醇(nf_MA0)、PTA(nf_TA0)、纯碱(nf_SA0)、玻璃(nf_FG0)、"
            + "乙二醇(nf_EG0)、苯乙烯(nf_EB0)、PVC(nf_V0)、橡胶(nf_RU0)、纸浆(nf_SP0)、"
            + "豆粕(nf_A0)、棕榈油(nf_P0)、菜油(nf_OI0)、玉米(nf_C0)、"
            + "白糖(nf_SR0)、棉花(nf_CF0)、苹果(nf_AP0)、生猪(nf_LH0)、鸡蛋(nf_JD0)、花生(nf_PK0)。"
            + "股指期货: 沪深300(nf_IF0)、中证500(nf_IC0)、上证50(nf_IH0)、中证1000(nf_IM0)。"
            + "国债期货: 十年国债(nf_T0)、五年国债(nf_TF0)、两年国债(nf_TS0)。"
             + "支持中文名称、代码或简称。设置 days 参数可生成走势图（chart_type=line 折线图，chart_type=candlestick K线图）。"
            + "注意: 国际期货(hf_)暂不支持历史K线图表，建议使用国内品种(如沪金/沪银)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> futures = new LinkedHashMap<>();
        futures.put("type", "string");
        futures.put("description", "期货名称或代码。"
            + "中文: 原油、黄金、白银、螺纹钢、铁矿石、焦炭、沪铜、甲醇、PTA、纯碱、玻璃、"
            + "豆粕、棕榈油、玉米、白糖、棉花、苹果、生猪、鸡蛋、沪深300等。"
            + "国际代码: hf_CL(原油)、hf_GC(黄金)、hf_SI(白银)等。"
            + "国内代码: nf_RB0(螺纹钢)、nf_I0(铁矿石)、nf_J0(焦炭)等。"
            + "简称: CL(原油)、GC(黄金)、RB0(螺纹钢)等自动识别。");
        properties.put("futures", futures);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "查询最近N个交易日的历史数据并生成走势图。"
            + "7=近一周，30=近一月，90=近三月。不填或0=只查实时行情。");
        properties.put("days", days);

        Map<String, Object> chartType = new LinkedHashMap<>();
        chartType.put("type", "string");
        chartType.put("description", "图表类型：不填=折线图（默认），line=折线图，candlestick=K线蜡烛图");
        chartType.put("enum", java.util.List.of("line", "candlestick"));
        properties.put("chart_type", chartType);

        params.put("properties", properties);
        params.put("required", java.util.List.of("futures"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String futures = arguments != null ? (String) arguments.get("futures") : null;
        if (futures == null || futures.isBlank()) {
            return ToolResult.text("错误: 请指定期货名称或代码\n"
                + "示例: 原油 / 黄金 / 螺纹钢 / hf_CL / CL");
        }

        int days = 0;
        if (arguments != null && arguments.get("days") != null) {
            try {
                days = Integer.parseInt(arguments.get("days").toString());
            } catch (NumberFormatException ignored) {}
        }

        String chartType = arguments != null ? (String) arguments.get("chart_type") : null;
        if (chartType == null || chartType.isBlank()) chartType = "line";

        log.info("FuturesTool 执行: futures={}, days={}, chart_type={}", futures, days, chartType);

        if (days > 0) {
            return generateChart(futures, days, chartType);
        }

        return ToolResult.text(futuresService.queryFutures(futures));
    }

    private ToolResult generateChart(String futures, int days, String chartType) {
        List<FuturesService.KlineData> history = futuresService.getHistoricalKline(futures, days);

        if (history.size() < 2) {
            String textResult = futuresService.queryFutures(futures);
            return ToolResult.text(textResult + "\n\n⚠️ 历史数据不足，无法生成图表");
        }

        String displayName = resolveDisplayName(futures);
        String code = futures;

        String period = switch (days) {
            case 7 -> "近一周";
            case 30 -> "近一月";
            case 90 -> "近三月";
            default -> "近" + days + "天";
        };

        byte[] chartBytes;
        String title;

        if ("candlestick".equals(chartType)) {
            List<ChartService.CandlestickPoint> candlePoints = new ArrayList<>();
            for (int i = 0; i < history.size(); i++) {
                FuturesService.KlineData k = history.get(i);
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
            String subtitle = period + "K线走势";
            chartBytes = chartService.generateCandlestickChart(title, subtitle, candlePoints);
        } else {
            List<ChartService.ChartPoint> linePoints = new ArrayList<>();
            for (int i = 0; i < history.size(); i++) {
                FuturesService.KlineData k = history.get(i);
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
            return ToolResult.text("⚠️ 图表生成失败");
        }

        String summary = buildChartSummary(displayName, code, days, history);
        return ToolResult.imageBytes(summary, chartBytes);
    }

    private String buildChartSummary(String displayName, String code, int days,
                                     List<FuturesService.KlineData> history) {
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

    private String resolveDisplayName(String input) {
        String upper = input.toUpperCase().replace("HF_", "").replace("NF_", "");
        return switch (upper) {
            case "CL" -> "WTI原油";
            case "GC" -> "黄金";
            case "SI" -> "白银";
            case "HG" -> "铜";
            case "NG" -> "天然气";
            case "ZC" -> "玉米";
            case "ZW" -> "小麦";
            case "ZS" -> "大豆";
            case "CT" -> "棉花";
            case "SB" -> "糖";
            case "PL" -> "铂金";
            case "PA" -> "钯金";
            case "SC0", "SC" -> "原油";
            case "RB0", "RB" -> "螺纹钢";
            case "HC0", "HC" -> "热卷";
            case "I0" -> "铁矿石";
            case "J0" -> "焦炭";
            case "JM0" -> "焦煤";
            case "CU0", "CU" -> "沪铜";
            case "AL0", "AL" -> "沪铝";
            case "ZN0", "ZN" -> "沪锌";
            case "NI0", "NI" -> "沪镍";
            case "SS0" -> "不锈钢";
            case "AU0", "AU" -> "沪金";
            case "AG0", "AG" -> "沪银";
            case "MA0", "MA" -> "甲醇";
            case "TA0", "TA" -> "PTA";
            case "SA0" -> "纯碱";
            case "FG0" -> "玻璃";
            case "EG0" -> "乙二醇";
            case "EB0" -> "苯乙烯";
            case "V0" -> "PVC";
            case "RU0" -> "橡胶";
            case "SP0" -> "纸浆";
            case "A0" -> "豆粕";
            case "P0" -> "棕榈油";
            case "OI0" -> "菜油";
            case "C0" -> "玉米";
            case "SR0" -> "白糖";
            case "CF0" -> "棉花";
            case "AP0" -> "苹果";
            case "LH0" -> "生猪";
            case "JD0" -> "鸡蛋";
            case "PK0" -> "花生";
            case "IF0" -> "沪深300";
            case "IC0" -> "中证500";
            case "IH0" -> "上证50";
            case "IM0" -> "中证1000";
            case "T0" -> "十年国债";
            case "TF0" -> "五年国债";
            case "TS0" -> "两年国债";
            default -> input;
        };
    }
}
