package com.example.youkeda_project.tool.life;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.TravelService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TravelTool extends AbstractTool {

    private final TravelService travelService;

    public TravelTool(TravelService travelService) {
        this.travelService = travelService;
    }

    @Override
    public String getName() {
        return "travel";
    }

    @Override
    public String getDescription() {
        return "出行路线查询工具.支持查询两个地点之间的多种出行方式对比,包括驾车(含打车费用估算),公交/地铁,骑行,步行."
            + "返回各方式的距离,耗时,费用和途经路线.如:从天河客运站到广州塔怎么走,北京到上海怎么去";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("type", "string");
        origin.put("description", "起点,如:天河客运站,北京,广州市天河区");
        properties.put("origin", origin);

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("type", "string");
        destination.put("description", "终点,如:广州塔,上海,深圳市南山区");
        properties.put("destination", destination);

        params.put("properties", properties);
        params.put("required", List.of("origin", "destination"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String origin = arguments != null ? (String) arguments.get("origin") : null;
        String destination = arguments != null ? (String) arguments.get("destination") : null;
        if (origin == null || destination == null) {
            return ToolResult.text("请提供起点和终点,如:从天河客运站到广州塔怎么走");
        }

        String result = travelService.query(origin.trim(), destination.trim());
        return ToolResult.text(result);
    }
}
