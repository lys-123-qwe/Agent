package com.example.youkeda_project.ai;

import java.util.List;

/**
 * AI 对话响应体,兼容 OpenAI/DeepSeek/通义千问 VL 的 chat completion 返回格式.
 */

public class ChatResponse {

    private String id;
    private List<ChatChoice> choices;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ChatChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<ChatChoice> choices) {
        this.choices = choices;
    }
}