package com.example.youkeda_project.tool.life;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.WeatherService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天气查询工具(Function Calling).
 * <p>
 * 定义 get_weather 函数,供 DeepSeek 通过 Function Calling 调用.
 * 内部委托 WeatherService 执行实际查询.
 */

@Component
public class WeatherTool extends AbstractTool {

        private final WeatherService weatherService;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Override
    public String getName() {
        return "get_weather";
    }

    @Override
    public String getDescription() {
        return "查询任意中国城市的实时天气信息,包括温度,湿度,风向等.通过高德地理编码获取城市坐标,再用和风天气API查询";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("type", "string");
        location.put("description", "城市名,如:北京,上海,广州,深圳,杭州,成都,武汉,南京,西安,重庆,长沙等");
        properties.put("location", location);

        params.put("properties", properties);
        params.put("required", java.util.List.of("location"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String location = arguments != null ? (String) arguments.get("location") : null;
        if (location == null || location.isBlank()) {
            return ToolResult.text("错误: 请指定城市名");
        }
        log.info("WeatherTool 执行: location={}", location);
        return ToolResult.text(weatherService.queryWeather(location));
    }
}
