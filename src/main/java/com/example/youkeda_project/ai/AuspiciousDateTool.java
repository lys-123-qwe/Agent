package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FortuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuspiciousDateTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(AuspiciousDateTool.class);
    private final FortuneService fortuneService;

    public AuspiciousDateTool(FortuneService fortuneService) {
        this.fortuneService = fortuneService;
    }

    @Override
    public String getName() { return "auspicious_date"; }

    @Override
    public String getDescription() {
        return "【选吉日】输入事项类型（结婚/搬家/开业/出行/入宅/订婚），推荐近期黄道吉日，含公历农历日期、宜忌、冲煞。适用于：选结婚日子、选搬家日子、看开业吉日。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> eventType = new LinkedHashMap<>();
        eventType.put("type", "string");
        eventType.put("description", "事项类型：结婚/搬家/开业/出行/入宅/订婚");
        eventType.put("enum", new String[]{"结婚","搬家","开业","出行","入宅","订婚"});
        p.put("event_type", eventType);
        Map<String, Object> year = new LinkedHashMap<>(); year.put("type", "integer"); year.put("description", "期望年份（可选，如2026）"); p.put("year", year);
        Map<String, Object> month = new LinkedHashMap<>(); month.put("type", "integer"); month.put("description", "期望月份（可选，1-12）"); p.put("month", month);
        params.put("properties", p);
        params.put("required", new String[]{"event_type"});
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (args == null) return ToolResult.text("请告诉我你想选什么日子的吉日（结婚/搬家/开业等）");
        String eventType = args.get("event_type") instanceof String s ? s : null;
        Integer year = args.get("year") instanceof Number n ? n.intValue() : null;
        Integer month = args.get("month") instanceof Number n ? n.intValue() : null;
        if (eventType == null || eventType.isBlank()) return ToolResult.text("请指定事项类型（结婚/搬家/开业等）");
        log.info("AuspiciousDateTool: {}", eventType);
        return ToolResult.text(fortuneService.auspiciousDate(eventType, year, month));
    }
}
