package com.example.youkeda_project.ai;

import java.util.List;

/**
 * AI 对话请求体，兼容 OpenAI/DeepSeek/通义千问 VL 的 chat completion 格式。
 */
public class ChatRequest {

    private String model;
    private List<ChatMessage> messages;

    public ChatRequest() {
    }

    public ChatRequest(String model, List<ChatMessage> messages) {
        this.model = model;
        this.messages = messages;
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
}