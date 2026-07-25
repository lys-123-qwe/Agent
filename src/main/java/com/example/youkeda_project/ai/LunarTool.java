package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FortuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LunarTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(LunarTool.class);
    private final FortuneService fortuneService;

    public LunarTool(FortuneService fortuneService) {
        this.fortuneService = fortuneService;
    }

    @Override
    public String getName() { return "lunar_calendar"; }

    @Override
    public String getDescription() {
        return "【农历日历查询】输入公历年月日，返回对应的农历日期、天干地支、生肖、节气、当日黄历宜忌。适用于：公历转农历、查农历、查黄历吉日、查节气。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> year = new LinkedHashMap<>(); year.put("type", "integer"); year.put("description", "公历年份"); p.put("year", year);
        Map<String, Object> month = new LinkedHashMap<>(); month.put("type", "integer"); month.put("description", "公历月份（1-12）"); p.put("month", month);
        Map<String, Object> day = new LinkedHashMap<>(); day.put("type", "integer"); day.put("description", "公历日期（1-31）"); p.put("day", day);
        params.put("properties", p);
        params.put("required", new String[]{"year","month","day"});
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (args == null) return ToolResult.text("请提供公历日期");
        Integer y = args.get("year") instanceof Number n ? n.intValue() : null;
        Integer m = args.get("month") instanceof Number n ? n.intValue() : null;
        Integer d = args.get("day") instanceof Number n ? n.intValue() : null;
        if (y == null || m == null || d == null) return ToolResult.text("请提供完整的年月日");
        if (m < 1 || m > 12 || d < 1 || d > 31) return ToolResult.text("日期不合法");
        log.info("LunarTool: {}-{}-{}", y, m, d);
        return ToolResult.text(fortuneService.lunarCalendar(y, m, d));
    }
}
