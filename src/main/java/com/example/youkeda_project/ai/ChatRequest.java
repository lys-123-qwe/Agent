package com.example.youkeda_project.ai;

import java.util.List;
import java.util.Map;

/**
 * AI 对话请求体,兼容 OpenAI/DeepSeek/通义千问 VL 的 chat completion 格式.
 * 支持 Function Calling 的 tools 字段.
 */
public class ChatRequest {

    private String model;
    private List<ChatMessage> messages;
    private List<Map<String, Object>> tools;

    public ChatRequest() {
    }

    public ChatRequest(String model, List<ChatMessage> messages) {
        this.model = model;
        this.messages = messages;
    }

    public ChatRequest(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
        this.model = model;
        this.messages = messages;
        this.tools = tools;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public List<Map<String, Object>> getTools() {
        return tools;
    }

    public void setTools(List<Map<String, Object>> tools) {
        this.tools = tools;
    }
}