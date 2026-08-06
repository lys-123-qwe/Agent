package com.example.youkeda_project.tool;

import java.util.Map;

/**
 * Function Calling 工具接口.
 * <p>
 * 每个工具定义名称,描述,参数 JSON Schema,
 * 供 DeepSeek/OpenAI Function Calling 使用.
 * execute() 返回 ToolResult,可携带文本结果和/或媒体数据.
 */

public interface Tool {

    String getName();

    String getDescription();

    Map<String, Object> getParameters();

    /**
     * 执行工具并返回结果.
     *
     * @param arguments 模型生成的参数 JSON(已解析为 Map)
     * @return 工具执行结果(文本 + 可选媒体)
     */
    ToolResult execute(Map<String, Object> arguments);

    default Map<String, Object> toToolDefinition() {
        var function = new java.util.LinkedHashMap<String, Object>();
        function.put("name", getName());
        function.put("description", getDescription());
        if (getParameters() != null) {
            function.put("parameters", getParameters());
        }

        var tool = new java.util.LinkedHashMap<String, Object>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }
}
