package com.example.youkeda_project.tool.life;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** 日期/时间/节日工具。 */


@Component
public class DateTimeTool extends AbstractTool {

        private static final List<Holiday> HOLIDAYS = List.of(
        new Holiday("元旦", MonthDay.of(1, 1)),
        new Holiday("春节", MonthDay.of(1, 29)),
        new Holiday("情人节", MonthDay.of(2, 14)),
        new Holiday("妇女节", MonthDay.of(3, 8)),
        new Holiday("植树节", MonthDay.of(3, 12)),
        new Holiday("愚人节", MonthDay.of(4, 1)),
        new Holiday("劳动节", MonthDay.of(5, 1)),
        new Holiday("端午节", MonthDay.of(5, 31)),
        new Holiday("儿童节", MonthDay.of(6, 1)),
        new Holiday("七夕节", MonthDay.of(8, 29)),
        new Holiday("中秋节", MonthDay.of(10, 6)),
        new Holiday("国庆节", MonthDay.of(10, 1)),
        new Holiday("万圣节", MonthDay.of(10, 31)),
        new Holiday("圣诞节", MonthDay.of(12, 25))
    );

    @Override
    public String getName() {
        return "date_time";
    }

    @Override
    public String getDescription() {
        return "日期时间计算工具.支持:计算日期间隔,日期加减,查询星期几,查询节假日倒计时."
            + "如:距离元旦还有几天,2025年3月15日是星期几,100天后是哪天";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("type", "string");
        operation.put("description", "操作类型:"
            + "\n- 'days_between': 计算两个日期之间的天数差,需提供 date1 和 date2"
            + "\n- 'add_days': 日期加上指定天数,需提供 date1 和 days"
            + "\n- 'day_of_week': 查询日期是星期几,需提供 date1"
            + "\n- 'countdown': 查询距离指定节日的倒计时,需提供 holiday_name"
            + "\n- 'now': 获取当前日期时间信息");
        operation.put("enum", List.of("days_between", "add_days", "day_of_week", "countdown", "now"));
        properties.put("operation", operation);

        Map<String, Object> date1 = new LinkedHashMap<>();
        date1.put("type", "string");
        date1.put("description", "日期1,格式 yyyy-MM-dd,如 2025-03-15");
        properties.put("date1", date1);

        Map<String, Object> date2 = new LinkedHashMap<>();
        date2.put("type", "string");
        date2.put("description", "日期2,格式 yyyy-MM-dd,如 2025-12-31");
        properties.put("date2", date2);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "天数(正数表示未来,负数表示过去)");
        properties.put("days", days);

        Map<String, Object> holidayName = new LinkedHashMap<>();
        holidayName.put("type", "string");
        holidayName.put("description", "节日名称,如:元旦,春节,国庆节,圣诞节,情人节");
        properties.put("holiday_name", holidayName);

        params.put("properties", properties);
        params.put("required", List.of("operation"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null || arguments.get("operation") == null) {
            return ToolResult.text("请指定操作类型: days_between / add_days / day_of_week / countdown / now");
        }

        String operation = (String) arguments.get("operation");

        return switch (operation) {
            case "days_between" -> daysBetween(arguments);
            case "add_days" -> addDays(arguments);
            case "day_of_week" -> dayOfWeek(arguments);
            case "countdown" -> countdown(arguments);
            case "now" -> now();
            default -> ToolResult.text("不支持的操作: " + operation);
        };
    }

    private ToolResult daysBetween(Map<String, Object> args) {
        String date1Str = (String) args.get("date1");
        String date2Str = (String) args.get("date2");
        if (date1Str == null || date2Str == null) {
            return ToolResult.text("请提供两个日期 date1 和 date2,格式 yyyy-MM-dd");
        }
        try {
            LocalDate d1 = LocalDate.parse(date1Str);
            LocalDate d2 = LocalDate.parse(date2Str);
            long days = ChronoUnit.DAYS.between(d1, d2);
            String result;
            if (days > 0) {
                result = date1Str + " 到 " + date2Str + " 还有 " + days + " 天";
            } else if (days < 0) {
                result = date2Str + " 已经过去 " + Math.abs(days) + " 天(相对于 " + date1Str + ")";
            } else {
                result = date1Str + " 和 " + date2Str + " 是同一天";
            }
            return ToolResult.text(result);
        } catch (Exception e) {
            return ToolResult.text("日期格式错误,请使用 yyyy-MM-dd 格式");
        }
    }

    private ToolResult addDays(Map<String, Object> args) {
        String dateStr = (String) args.get("date1");
        Object daysObj = args.get("days");
        if (dateStr == null || daysObj == null) {
            return ToolResult.text("请提供 date1(日期)和 days(天数)");
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            int days = ((Number) daysObj).intValue();
            LocalDate result = date.plusDays(days);
            String sign = days >= 0 ? "后" : "前";
            return ToolResult.text(dateStr + " 的 " + Math.abs(days) + " 天" + sign + "是 " + result);
        } catch (Exception e) {
            return ToolResult.text("日期格式错误,请使用 yyyy-MM-dd 格式");
        }
    }

    private ToolResult dayOfWeek(Map<String, Object> args) {
        String dateStr = (String) args.get("date1");
        if (dateStr == null) {
            return ToolResult.text("请提供 date1(日期,格式 yyyy-MM-dd)");
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            DayOfWeek dow = date.getDayOfWeek();
            String cnName = switch (dow) {
                case MONDAY -> "星期一";
                case TUESDAY -> "星期二";
                case WEDNESDAY -> "星期三";
                case THURSDAY -> "星期四";
                case FRIDAY -> "星期五";
                case SATURDAY -> "星期六";
                case SUNDAY -> "星期日";
            };
            return ToolResult.text(dateStr + " 是 " + cnName);
        } catch (Exception e) {
            return ToolResult.text("日期格式错误,请使用 yyyy-MM-dd 格式");
        }
    }

    private ToolResult countdown(Map<String, Object> args) {
        String name = (String) args.get("holiday_name");
        if (name == null || name.isBlank()) {
            StringBuilder sb = new StringBuilder("支持的节日:\n");
            for (Holiday h : HOLIDAYS) {
                sb.append("- ").append(h.name).append("(").append(h.date).append(")\n");
            }
            return ToolResult.text(sb.toString());
        }

        LocalDate today = LocalDate.now();
        for (Holiday h : HOLIDAYS) {
            if (h.name.equals(name) || name.contains(h.name)) {
                LocalDate holidayDate = LocalDate.of(today.getYear(), h.date.getMonth(), h.date.getDayOfMonth());
                if (holidayDate.isBefore(today) || holidayDate.isEqual(today)) {
                    holidayDate = holidayDate.plusYears(1);
                }
                long days = ChronoUnit.DAYS.between(today, holidayDate);
                return ToolResult.text("距离 " + h.name + "(" + holidayDate + ")还有 " + days + " 天");
            }
        }
        return ToolResult.text("不支持的节日: " + name);
    }

    private ToolResult now() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        String cnName = switch (dow) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("📅 当前日期: ").append(today).append(" ").append(cnName).append("\n");

        for (Holiday h : HOLIDAYS) {
            LocalDate hd = LocalDate.of(today.getYear(), h.date.getMonth(), h.date.getDayOfMonth());
            if (hd.isAfter(today) || hd.isEqual(today)) {
                long days = ChronoUnit.DAYS.between(today, hd);
                sb.append(hd.isEqual(today)
                    ? "🎉 今天就是 " + h.name + "!"
                    : "⏳ 距离 " + h.name + " 还有 " + days + " 天");
                break;
            }
        }

        return ToolResult.text(sb.toString());
    }

    private record Holiday(String name, MonthDay date) {}
}
