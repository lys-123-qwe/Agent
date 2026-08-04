package com.example.youkeda_project.tool.life;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.ConstellationService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConstellationTool extends AbstractTool {

    private final ConstellationService constellationService;

    public ConstellationTool(ConstellationService constellationService) {
        this.constellationService = constellationService;
    }

    @Override
    public String getName() {
        return "constellation";
    }

    @Override
    public String getDescription() {
        return "星座查询和每日运势.支持:根据出生日期查星座(传入 month+day),查询某个星座的今日运势(传入 sign)."
            + "注意:如果你不知道今天的日期,请先调用 date_time 工具的 now 操作获取当前日期,"
            + "再将获取到的月,日作为 month 和 day 参数传入本工具进行星座查询."
            + "如:先调 date_time(now) 得到今天日期,再调 constellation(month=X, day=X) 查星座."
            + "或者:用户问'今天双子座运势'则直接传 sign='双子座'.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> sign = new LinkedHashMap<>();
        sign.put("type", "string");
        sign.put("description", "星座中文名,如:白羊座,金牛座,双子座,巨蟹座,狮子座,处女座,天秤座,天蝎座,射手座,摩羯座,水瓶座,双鱼座");
        properties.put("sign", sign);

        Map<String, Object> month = new LinkedHashMap<>();
        month.put("type", "integer");
        month.put("description", "月份(1-12),用于查指定日期是什么星座");
        properties.put("month", month);

        Map<String, Object> day = new LinkedHashMap<>();
        day.put("type", "integer");
        day.put("description", "日期(1-31),配合 month 使用");
        properties.put("day", day);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null || (arguments.get("month") == null && arguments.get("sign") == null)) {
            return ToolResult.text("请提供出生日期或星座名称,如:3月15日是什么星座,今天双子座运势");
        }

        if (arguments.containsKey("month") && arguments.containsKey("day")) {
            int month = ((Number) arguments.get("month")).intValue();
            int dayVal = ((Number) arguments.get("day")).intValue();
            return ToolResult.text(constellationService.queryByDate(month, dayVal));
        }

        String sign = (String) arguments.get("sign");
        if (sign == null || sign.isBlank()) {
            return ToolResult.text("请提供星座名称");
        }

        return ToolResult.text(constellationService.queryHoroscope(sign));
    }
}
