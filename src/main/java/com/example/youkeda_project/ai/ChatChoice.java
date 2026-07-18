package com.example.youkeda_project.ai;

/**
 * AI 对话响应的选项（choice）数据结构
 * <p>
 * 兼容 OpenAI/DeepSeek/通义千问 VL 的 chat completion 响应格式。
 */
public class ChatChoice {

    private int index;
    private ChatMessage message;

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
}