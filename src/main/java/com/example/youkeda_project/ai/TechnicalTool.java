package com.example.youkeda_project.ai;

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

    public TechnicalTool(KLineService kLineService, TechnicalService technicalService) {
        this.kLineService = kLineService;
        this.technicalService = technicalService;
    }

    @Override
    public String getName() {
        return "stock_technical";
    }

    @Override
    public String getDescription() {
        return "技术分析：查询K线数据、计算移动平均线(MA)和相对强弱指数(RSI)。"
            + "支持A股和期货。多只用逗号分隔，如：'贵州茅台,五粮液' 或 '沪金,螺纹钢,原油'。"
            + "默认计算 MA5/MA10/MA20/MA60 和 RSI6/RSI12/RSI24。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> code = new LinkedHashMap<>();
        code.put("type", "string");
        code.put("description", "股票或期货名称/代码，支持多只（逗号分隔）。股票：贵州茅台、sh600519。期货：沪金、螺纹钢、AU888、RB888。多个：'贵州茅台,五粮液' 或 '沪金,螺纹钢'");
        properties.put("code", code);

        Map<String, Object> ma = new LinkedHashMap<>();
        ma.put("type", "string");
        ma.put("description", "MA周期，逗号分隔。如 '5,10,20,60'，默认 5,10,20,60。传空字符串则不计算MA");
        properties.put("ma", ma);

        Map<String, Object> rsi = new LinkedHashMap<>();
        rsi.put("type", "string");
        rsi.put("description", "RSI周期，逗号分隔。如 '6,12,24'，默认 6,12,24。传空字符串则不计算RSI");
        properties.put("rsi", rsi);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "获取最近多少个交易日的数据（默认120，最多500）");
        properties.put("days", days);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String code = arguments != null ? (String) arguments.get("code") : null;
        if (code == null || code.isBlank()) {
            return ToolResult.text("请指定股票名称或代码");
        }

        int days = 120;
        if (arguments != null && arguments.get("days") instanceof Number n) {
            days = Math.max(20, Math.min(n.intValue(), 500));
        }

        List<Integer> maPeriods = List.of(5, 10, 20, 60);
        List<Integer> rsiPeriods = List.of(6, 12, 24);

        if (arguments != null) {
            if (arguments.get("ma") instanceof String ms && !ms.isBlank()) {
                maPeriods = parsePeriods(ms);
            } else if (arguments.get("ma") instanceof String ms && ms.isBlank()) {
                maPeriods = List.of();
            }
            if (arguments.get("rsi") instanceof String rs && !rs.isBlank()) {
                rsiPeriods = parsePeriods(rs);
            } else if (arguments.get("rsi") instanceof String rs && rs.isBlank()) {
                rsiPeriods = List.of();
            }
        }

        log.info("TechnicalTool: code={}, days={}, ma={}, rsi={}", code, days, maPeriods, rsiPeriods);

        String[] codes = code.split("[,，、\\s]+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < codes.length; i++) {
            String single = codes[i].trim();
            if (single.isBlank()) continue;

            List<KLineService.KLineData> d;
            String name;
            boolean isFut = kLineService.isFutures(single);

            if (isFut) {
                d = kLineService.fetchFuturesDailyKLine(single, days);
                name = single;
            } else {
                d = kLineService.fetchDailyKLine(single, days);
                name = kLineService.resolveSymbol(single);
            }

            if (d == null || d.isEmpty()) {
                sb.append("未找到 ").append(single).append(" 的数据，请检查名称或代码\n");
                continue;
            }

            if (i > 0) sb.append("\n---\n");
            if (name == null) name = single;

            sb.append("【").append(name).append("】技术分析\n");
            sb.append(technicalService.formatAnalysis(d, maPeriods, rsiPeriods));
            sb.append(technicalService.formatKLine(d));
        }

        if (sb.isEmpty()) {
            return ToolResult.text("未找到 " + code + " 的K线数据，请检查股票名称或代码是否正确");
        }
        return ToolResult.text(sb.toString());
    }

    private List<Integer> parsePeriods(String s) {
        var list = new java.util.ArrayList<Integer>();
        for (String p : s.split(",")) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v > 0) list.add(v);
            } catch (NumberFormatException ignored) {}
        }
        return list;
    }
}
