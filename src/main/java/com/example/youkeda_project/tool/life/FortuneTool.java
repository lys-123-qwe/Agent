package com.example.youkeda_project.tool.life;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.FortuneService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FortuneTool extends AbstractTool {

    private final FortuneService fortuneService;

    public FortuneTool(FortuneService fortuneService) {
        this.fortuneService = fortuneService;
    }

    @Override
    public String getName() {
        return "fortune_telling";
    }

    @Override
    public String getDescription() {
        return """
            运势/命理工具，支持以下类型：
            - bazi：八字排盘（需 year/month/day/hour，可选 minute/gender/name）
            - ziwei：紫微斗数（需 year/month/day/hour，可选 minute/gender/name）
            - iching：六爻占卜（需 question + gua_yao1~6，硬币结果：0=少阴 1=少阳 2=老阴 3=老阳）
            - cross：生肖血型星座综合分析（需 year/month/day/blood_type，可选 gender）
            - lunar：农历黄历查询（需 year/month/day）
            - auspicious_date：择吉日（需 event_type，可选 year/month）
            """;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        properties.put("type", Map.of(
            "type", "string",
            "enum", java.util.List.of("bazi", "ziwei", "iching", "cross", "lunar", "auspicious_date"),
            "description", "算命类型"
        ));
        properties.put("year", Map.of("type", "integer", "description", "出生年份（如 1995）"));
        properties.put("month", Map.of("type", "integer", "description", "月份（1-12）"));
        properties.put("day", Map.of("type", "integer", "description", "日期（1-31）"));
        properties.put("hour", Map.of("type", "integer", "description", "小时（0-23）"));
        properties.put("minute", Map.of("type", "integer", "description", "分钟（0-59）"));
        properties.put("gender", Map.of("type", "integer", "enum", java.util.List.of(0, 1), "description", "性别（1=男 0=女）"));
        properties.put("name", Map.of("type", "string", "description", "姓名"));
        properties.put("calendar_type", Map.of("type", "integer", "enum", java.util.List.of(0, 1), "description", "历类型（0=农历 1=公历，默认1，仅 bazi/ziwei 使用）"));
        properties.put("question", Map.of("type", "string", "description", "占卜问题（仅 iching 使用）"));
        properties.put("gua_yao1", Map.of("type", "integer", "enum", java.util.List.of(0, 1, 2, 3), "description", "六爻初爻（第一次抛硬币结果 0=少阴 1=少阳 2=老阴 3=老阳，仅 iching 手工起卦使用）"));
        properties.put("gua_yao2", Map.of("type", "integer", "enum", java.util.List.of(0, 1, 2, 3), "description", "六爻二爻（第二次抛硬币结果，仅 iching 手工起卦使用）"));
        properties.put("gua_yao3", Map.of("type", "integer", "enum", java.util.List.of(0, 1, 2, 3), "description", "六爻三爻（第三次抛硬币结果，仅 iching 手工起卦使用）"));
        properties.put("gua_yao4", Map.of("type", "integer", "enum", java.util.List.of(0, 1, 2, 3), "description", "六爻四爻（第四次抛硬币结果，仅 iching 手工起卦使用）"));
        properties.put("gua_yao5", Map.of("type", "integer", "enum", java.util.List.of(0, 1, 2, 3), "description", "六爻五爻（第五次抛硬币结果，仅 iching 手工起卦使用）"));
        properties.put("gua_yao6", Map.of("type", "integer", "enum", java.util.List.of(0, 1, 2, 3), "description", "六爻六爻（第六次抛硬币结果，仅 iching 手工起卦使用）"));
        properties.put("event_type", Map.of(
            "type", "string",
            "enum", java.util.List.of("结婚", "搬家", "开业", "出行", "入宅", "订婚"),
            "description", "事项类型（仅 auspicious_date 使用）"
        ));
        properties.put("blood_type", Map.of(
            "type", "string",
            "enum", java.util.List.of("A", "B", "O", "AB"),
            "description", "血型（仅 cross 使用）"
        ));
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("type"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (args == null || !args.containsKey("type")) {
            return ToolResult.text("错误：必须指定 type 参数（bazi/ziwei/iching/cross/lunar/auspicious_date）");
        }

        String type = String.valueOf(args.get("type")).toLowerCase();
        return switch (type) {
            case "bazi" -> executeBazi(args);
            case "ziwei" -> executeZiwei(args);
            case "iching" -> executeIching(args);
            case "cross" -> executeCross(args);
            case "lunar" -> executeLunar(args);
            case "auspicious_date" -> executeAuspiciousDate(args);
            default -> ToolResult.text("未知的算命类型：" + type + "，支持：bazi/ziwei/iching/cross/lunar/auspicious_date");
        };
    }

    private ToolResult executeBazi(Map<String, Object> args) {
        Integer year = intVal(args, "year");
        Integer month = intVal(args, "month");
        Integer day = intVal(args, "day");
        Integer hour = intVal(args, "hour");
        if (year == null || month == null || day == null || hour == null) {
            return ToolResult.text("八字排盘需要提供 year/month/day/hour");
        }
        Integer minute = intVal(args, "minute");
        Integer gender = intVal(args, "gender");
        String name = str(args, "name");
        Integer calendarType = intVal(args, "calendar_type");
        return ToolResult.text(fortuneService.bazi(year, month, day, hour, minute != null ? minute : 0, gender, name, calendarType != null ? calendarType : 1));
    }

    private ToolResult executeZiwei(Map<String, Object> args) {
        Integer year = intVal(args, "year");
        Integer month = intVal(args, "month");
        Integer day = intVal(args, "day");
        Integer hour = intVal(args, "hour");
        if (year == null || month == null || day == null || hour == null) {
            return ToolResult.text("紫微斗数需要提供 year/month/day/hour");
        }
        Integer minute = intVal(args, "minute");
        Integer gender = intVal(args, "gender");
        String name = str(args, "name");
        Integer calendarType = intVal(args, "calendar_type");
        return ToolResult.text(fortuneService.ziwei(year, month, day, hour, minute != null ? minute : 0, gender, name, calendarType != null ? calendarType : 1));
    }

    private ToolResult executeIching(Map<String, Object> args) {
        String question = str(args, "question");
        if (question == null || question.isBlank()) {
            return ToolResult.text("六爻占卜需要提供 question（占卜问题）");
        }
        Integer g1 = intVal(args, "gua_yao1");
        Integer g2 = intVal(args, "gua_yao2");
        Integer g3 = intVal(args, "gua_yao3");
        Integer g4 = intVal(args, "gua_yao4");
        Integer g5 = intVal(args, "gua_yao5");
        Integer g6 = intVal(args, "gua_yao6");
        if (g1 == null || g2 == null || g3 == null || g4 == null || g5 == null || g6 == null) {
            return ToolResult.text("六爻占卜需要 6 次抛硬币结果。抛 3 枚硬币 6 次：3正=老阳(3) 2正1反=少阳(1) 1正2反=少阴(0) 3反=老阴(2)，从初爻到六爻依次传入 gua_yao1~6");
        }
        int[] guaYaos = new int[]{g1, g2, g3, g4, g5, g6};
        return ToolResult.text(fortuneService.iching(question, guaYaos));
    }

    private ToolResult executeCross(Map<String, Object> args) {
        Integer year = intVal(args, "year");
        Integer month = intVal(args, "month");
        Integer day = intVal(args, "day");
        String bloodType = str(args, "blood_type");
        if (year == null || month == null || day == null || bloodType == null) {
            return ToolResult.text("综合分析需要提供 year/month/day/blood_type");
        }
        Integer gender = intVal(args, "gender");
        return ToolResult.text(fortuneService.crossAnalysis(year, month, day, bloodType, gender));
    }

    private ToolResult executeLunar(Map<String, Object> args) {
        Integer year = intVal(args, "year");
        Integer month = intVal(args, "month");
        Integer day = intVal(args, "day");
        if (year == null || month == null || day == null) {
            return ToolResult.text("农历查询需要提供 year/month/day");
        }
        return ToolResult.text(fortuneService.lunarCalendar(year, month, day));
    }

    private ToolResult executeAuspiciousDate(Map<String, Object> args) {
        String eventType = str(args, "event_type");
        if (eventType == null || eventType.isBlank()) {
            return ToolResult.text("择吉日需要提供 event_type（结婚/搬家/开业/出行/入宅/订婚）");
        }
        Integer year = intVal(args, "year");
        Integer month = intVal(args, "month");
        return ToolResult.text(fortuneService.auspiciousDate(eventType, year, month));
    }
}