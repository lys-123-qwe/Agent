package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FuturesService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FuturesTool implements Tool {

    private final FuturesService futuresService;

    public FuturesTool(FuturesService futuresService) {
        this.futuresService = futuresService;
    }

    @Override
    public String getName() {
        return "futures";
    }

    @Override
    public String getDescription() {
        return "查询国内期货实时行情。支持主力合约和品种名称查询。"
            + "如：螺纹钢什么价、沪金期货、原油行情、IF主力、RB888";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> code = new LinkedHashMap<>();
        code.put("type", "string");
        code.put("description", "期货品种名称或合约代码，如：沪金、螺纹钢、原油、IF、RB888、AU888。"
            + "支持：股指(IF/IH/IC/IM)、贵金属(沪金/沪银)、有色(沪铜/沪铝/沪锌/沪镍)、"
            + "黑色(螺纹钢/铁矿石/焦煤/焦炭)、化工(原油/甲醇/PTA)、农产品(豆粕/白糖/棉花/玉米)");
        properties.put("code", code);

        params.put("properties", properties);
        params.put("required", List.of("code"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String code = arguments != null ? (String) arguments.get("code") : null;
        if (code == null || code.isBlank()) {
            return ToolResult.text("请提供期货品种名称或代码，如：螺纹钢什么价、沪金期货");
        }

        String result = futuresService.query(code.trim());
        if (result == null) {
            return ToolResult.text("期货行情查询失败，可能是非交易时间或代码不准确");
        }
        return ToolResult.text(result);
    }
}
