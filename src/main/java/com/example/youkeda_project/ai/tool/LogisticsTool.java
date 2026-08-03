package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.assist.LogisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物流查询 Function Calling 工具.
 * <p>
 * 封装 {@link LogisticsService},供 DeepSeek/OpenAI 模型在对话中自动调用.
 */
@Component
public class LogisticsTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(LogisticsTool.class);

    private final LogisticsService logisticsService;

    public LogisticsTool(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @Override
    public String getName() {
        return "query_express";
    }

    @Override
    public String getDescription() {
        return "查询快递物流信息.支持顺丰,中通,圆通,韵达,申通,极兔,京东,邮政,EMS等主流快递,自动识别快递公司";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> number = new LinkedHashMap<>();
        number.put("type", "string");
        number.put("description", "快递单号,如:SF1234567890,YT1234567890");

        Map<String, Object> courier = new LinkedHashMap<>();
        courier.put("type", "string");
        courier.put("description", "快递公司名称(可选),如:顺丰,中通,圆通,韵达,申通,极兔,京东,邮政.不填则自动识别");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("number", number);
        properties.put("courier", courier);

        params.put("properties", properties);
        params.put("required", java.util.List.of("number"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String number = arguments != null ? (String) arguments.get("number") : null;
        String courier = arguments != null ? (String) arguments.get("courier") : null;
        if (number == null || number.isBlank()) {
            return ToolResult.text("错误:请提供快递单号");
        }
        log.info("LogisticsTool 执行: number={}, courier={}", number, courier);
        try {
            return ToolResult.text(logisticsService.query(number, courier));
        } catch (Exception e) {
            log.error("物流查询失败: {}", e.getMessage());
            return ToolResult.text("物流查询失败: " + e.getMessage());
        }
    }
}
