package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.BaziService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BaziTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BaziTool.class);

    private final BaziService baziService;

    public BaziTool(BaziService baziService) {
        this.baziService = baziService;
    }

    @Override
    public String getName() {
        return "bazi_fortune";
    }

    @Override
    public String getDescription() {
        return "【八字算命】根据出生年月日时排盘四柱八字（公历/农历均可），推算十天干十二地支、五行生克、"
            + "十神关系、大运流年、性格事业感情分析、今日宜忌。传入 year/month/day/hour/minute/gender 进行八字推算。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> year = new LinkedHashMap<>();
        year.put("type", "integer");
        year.put("description", "出生年份（四位数字，如 1995）");
        properties.put("year", year);

        Map<String, Object> month = new LinkedHashMap<>();
        month.put("type", "integer");
        month.put("description", "出生月份（1-12）");
        properties.put("month", month);

        Map<String, Object> day = new LinkedHashMap<>();
        day.put("type", "integer");
        day.put("description", "出生日期（1-31）");
        properties.put("day", day);

        Map<String, Object> hour = new LinkedHashMap<>();
        hour.put("type", "integer");
        hour.put("description", "出生小时（0-23）");
        properties.put("hour", hour);

        Map<String, Object> minute = new LinkedHashMap<>();
        minute.put("type", "integer");
        minute.put("description", "出生分钟（0-59，可选，默认0）");
        properties.put("minute", minute);

        Map<String, Object> gender = new LinkedHashMap<>();
        gender.put("type", "integer");
        gender.put("description", "性别（1=男，0=女，可选）");
        gender.put("enum", new Integer[]{0, 1});
        properties.put("gender", gender);

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "姓名（可选）");
        properties.put("name", name);

        params.put("properties", properties);
        params.put("required", new String[]{"year", "month", "day", "hour"});
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null) {
            return ToolResult.text("请提供出生年月日时");
        }

        Integer year = arguments.get("year") instanceof Number n ? n.intValue() : null;
        Integer month = arguments.get("month") instanceof Number n ? n.intValue() : null;
        Integer day = arguments.get("day") instanceof Number n ? n.intValue() : null;
        Integer hour = arguments.get("hour") instanceof Number n ? n.intValue() : null;
        Integer minute = arguments.get("minute") instanceof Number n ? n.intValue() : 0;
        Integer gender = arguments.get("gender") instanceof Number n ? n.intValue() : null;
        String name = arguments.get("name") instanceof String s ? s : null;

        if (year == null || month == null || day == null || hour == null) {
            return ToolResult.text("请提供完整的出生年月日时（year/month/day/hour）");
        }

        if (year < 1900 || year > 2100) {
            return ToolResult.text("年份范围：1900-2100");
        }
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour < 0 || hour > 23) {
            return ToolResult.text("日期或时间不合法，请检查输入");
        }

        log.info("BaziTool: {}-{}-{}-{}-{}, gender={}, name={}", year, month, day, hour, minute, gender, name);
        String result = baziService.calculate(year, month, day, hour, minute, gender, name);
        return ToolResult.text(result);
    }
}
