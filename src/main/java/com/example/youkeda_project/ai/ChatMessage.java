package com.example.youkeda_project.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 对话消息体（record）
 * <p>
 * role: system/user/assistant，content 既可以是 String（纯文本），
 * 也可以是 List<Map>（多模态：文字 + 图片 URL），兼容通义千问 VL 格式。
 */
public record ChatMessage(
    @JsonProperty("role") String role,
    @JsonProperty("content") Object content
) {}
