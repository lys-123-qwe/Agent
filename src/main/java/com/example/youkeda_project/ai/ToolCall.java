package com.example.youkeda_project.ai;

import java.util.List;

/**
 * 解析后的工具调用信息，对应 OpenAI/DeepSeek 响应中的 tool_calls。
 */
public record ToolCall(
    String id,
    String functionName,
    String arguments
) {
    /**
     * 将一条 tool_calls 条目解析为 ToolCall 对象。
     */
    @SuppressWarnings("unchecked")
    public static ToolCall fromMap(java.util.Map<String, Object> map) {
        String id = (String) map.get("id");
        java.util.Map<String, Object> function = (java.util.Map<String, Object>) map.get("function");
        String name = function != null ? (String) function.get("name") : null;
        String args = function != null ? (String) function.get("arguments") : null;
        return new ToolCall(id, name, args);
    }

    /**
     * 从 ChatChoice 中提取 tool_calls 列表。
     */
    @SuppressWarnings("unchecked")
    public static List<ToolCall> fromChoice(ChatChoice choice) {
        Object raw = choice.getToolCalls();
        if (raw instanceof List<?> list) {
            return list.stream()
                .filter(e -> e instanceof java.util.Map)
                .map(e -> ToolCall.fromMap((java.util.Map<String, Object>) e))
                .toList();
        }
        return List.of();
    }
}
