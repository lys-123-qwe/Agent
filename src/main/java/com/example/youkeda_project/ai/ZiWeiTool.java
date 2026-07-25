package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FortuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ZiWeiTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiTool.class);
    private final FortuneService fortuneService;

    public ZiWeiTool(FortuneService fortuneService) {
        this.fortuneService = fortuneService;
    }

    @Override
    public String getName() { return "ziwei_fortune"; }

    @Override
    public String getDescription() {
        return "【紫微斗数排盘】输入出生年月日时，排十四主星十二宫命盘，分析性格/事业/财运/感情/大限运势。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> year = new LinkedHashMap<>(); year.put("type", "integer"); year.put("description", "出生年份"); p.put("year", year);
        Map<String, Object> month = new LinkedHashMap<>(); month.put("type", "integer"); month.put("description", "出生月份（1-12）"); p.put("month", month);
        Map<String, Object> day = new LinkedHashMap<>(); day.put("type", "integer"); day.put("description", "出生日期（1-31）"); p.put("day", day);
        Map<String, Object> hour = new LinkedHashMap<>(); hour.put("type", "integer"); hour.put("description", "出生小时（0-23）"); p.put("hour", hour);
        Map<String, Object> minute = new LinkedHashMap<>(); minute.put("type", "integer"); minute.put("description", "出生分钟"); p.put("minute", minute);
        Map<String, Object> gender = new LinkedHashMap<>(); gender.put("type", "integer"); gender.put("description", "性别：1男/0女"); gender.put("enum", new Integer[]{0,1}); p.put("gender", gender);
        Map<String, Object> name = new LinkedHashMap<>(); name.put("type", "string"); name.put("description", "姓名（可选）"); p.put("name", name);
        params.put("properties", p);
        params.put("required", new String[]{"year","month","day","hour"});
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (args == null) return ToolResult.text("请提供出生年月日时");
        Integer y = args.get("year") instanceof Number n ? n.intValue() : null;
        Integer m = args.get("month") instanceof Number n ? n.intValue() : null;
        Integer d = args.get("day") instanceof Number n ? n.intValue() : null;
        Integer h = args.get("hour") instanceof Number n ? n.intValue() : null;
        Integer min = args.get("minute") instanceof Number n ? n.intValue() : 0;
        Integer g = args.get("gender") instanceof Number n ? n.intValue() : null;
        String name = args.get("name") instanceof String s ? s : null;
        if (y == null || m == null || d == null || h == null) return ToolResult.text("请提供完整出生信息");
        log.info("ZiWeiTool: {}-{}-{}-{}:{}", y, m, d, h, min);
        return ToolResult.text(fortuneService.ziwei(y, m, d, h, min, g, name));
    }
}
