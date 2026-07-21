package com.example.youkeda_project.ai;

import java.util.Map;

/**
 * Function Calling 工具接口。
 * <p>
 * 每个工具定义名称、描述、参数 JSON Schema，
 * 供 DeepSeek/OpenAI Function Calling 使用。
 */
public interface Tool {

    /**
     * 工具名称（函数名），必须是 a-z, A-Z, 0-9, 下划线或短横线。
     */
    String getName();

    /**
     * 工具描述，供模型决定何时调用。
     */
    String getDescription();

    /**
     * 参数的 JSON Schema 定义。
     * 返回 null 表示无参数。
     */
    Map<String, Object> getParameters();

    /**
     * 执行工具并返回结果字符串。
     *
     * @param arguments 模型生成的参数 JSON（已解析为 Map）
     * @return 工具执行结果
     */
    String execute(Map<String, Object> arguments);

    /**
     * 将此工具转换为 OpenAI/DeepSeek tools 数组中的一个元素。
     */
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
