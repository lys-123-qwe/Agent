package com.example.youkeda_project.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 对话消息体。
 * <p>
 * role: system/user/assistant/tool
 * content: String（纯文本）或 List<Map>（多模态）或 null（有 tool_calls 时）
 * tool_call_id: 工具调用 ID，仅 tool role 使用
 * name: 工具名称，仅 tool role 使用
 * toolCalls: 工具调用列表，仅 assistant role 使用（Function Calling）
 */
public record ChatMessage(
    @JsonProperty("role") String role,
    @JsonProperty("content") Object content,
    @JsonProperty("tool_call_id") String toolCallId,
    @JsonProperty("name") String name,
    @JsonProperty("tool_calls") Object toolCalls
) {
    public ChatMessage(String role, String content) {
        this(role, content, null, null, null);
    }

    public ChatMessage(String role, Object content) {
        this(role, content, null, null, null);
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        return new ChatMessage("tool", content, toolCallId, name, null);
    }

    public static ChatMessage assistantWithToolCalls(Object toolCalls) {
        return new ChatMessage("assistant", null, null, null, toolCalls);
    }
}
