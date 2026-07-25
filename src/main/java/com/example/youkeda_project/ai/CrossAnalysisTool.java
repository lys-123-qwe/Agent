package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FortuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CrossAnalysisTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CrossAnalysisTool.class);
    private final FortuneService fortuneService;

    public CrossAnalysisTool(FortuneService fortuneService) {
        this.fortuneService = fortuneService;
    }

    @Override
    public String getName() { return "cross_analysis"; }

    @Override
    public String getDescription() {
        return "【生肖+血型+星座交叉分析】输入出生年月日、血型（A/B/O/AB），综合分析性格特质、职业方向和人际建议。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> year = new LinkedHashMap<>(); year.put("type", "integer"); year.put("description", "出生年份"); p.put("year", year);
        Map<String, Object> month = new LinkedHashMap<>(); month.put("type", "integer"); month.put("description", "出生月份"); p.put("month", month);
        Map<String, Object> day = new LinkedHashMap<>(); day.put("type", "integer"); day.put("description", "出生日期"); p.put("day", day);
        Map<String, Object> bloodType = new LinkedHashMap<>(); bloodType.put("type", "string"); bloodType.put("description", "血型（A/B/O/AB）"); bloodType.put("enum", new String[]{"A","B","O","AB"}); p.put("blood_type", bloodType);
        Map<String, Object> gender = new LinkedHashMap<>(); gender.put("type", "integer"); gender.put("description", "性别(1男/0女，可选)"); gender.put("enum", new Integer[]{0,1}); p.put("gender", gender);
        params.put("properties", p);
        params.put("required", new String[]{"year","month","day","blood_type"});
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (args == null) return ToolResult.text("请提供出生年月日和血型");
        Integer y = args.get("year") instanceof Number n ? n.intValue() : null;
        Integer m = args.get("month") instanceof Number n ? n.intValue() : null;
        Integer d = args.get("day") instanceof Number n ? n.intValue() : null;
        String bt = args.get("blood_type") instanceof String s ? s : null;
        Integer g = args.get("gender") instanceof Number n ? n.intValue() : null;
        if (y == null || m == null || d == null || bt == null) return ToolResult.text("请提供完整的出生年月日和血型");
        log.info("CrossAnalysisTool: {}-{}-{}, blood={}", y, m, d, bt);
        return ToolResult.text(fortuneService.crossAnalysis(y, m, d, bt, g));
    }
}
