package com.example.youkeda_project.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 对话响应的选项(choice)数据结构.
 * 兼容 OpenAI/DeepSeek/通义千问 VL 的 chat completion 响应格式.
 * 支持 Function Calling: finish_reason=tool_calls 和 tool_calls 字段.
 */
public class ChatChoice {

    private int index;
    private ChatMessage message;
    @JsonProperty("finish_reason")
    private String finishReason;
    @JsonProperty("tool_calls")
    private Object toolCalls;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public void setMessage(ChatMessage message) {
        this.message = message;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Object getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(Object toolCalls) {
        this.toolCalls = toolCalls;
    }

    public boolean isToolCall() {
        if (!"tool_calls".equals(finishReason)) return false;
        if (toolCalls != null) return true;
        return message != null && message.toolCalls() != null;
    }
}
