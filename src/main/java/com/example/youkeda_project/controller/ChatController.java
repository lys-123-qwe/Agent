package com.example.youkeda_project.controller;

import com.example.youkeda_project.ai.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * Web 前端聊天接口（SSE 流式）。
 * <p>
 * 前端通过 GET /api/chat/stream?msg=xxx 订阅流式回复，
 * 后端逐段推送 {delta} JSON，结束时发送 [DONE]。
 * 此接口仅供 Web 前端使用，微信侧仍走原有流程。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AIService aiService;

    public ChatController(AIService aiService) {
        this.aiService = aiService;
    }

    /** SSE 流式聊天端点 */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "web") String userId,
                             @RequestParam String msg) {
        SseEmitter emitter = new SseEmitter(120_000L);
        new Thread(() -> {
            try {
                aiService.chatStream(userId, msg, delta -> {
                    try {
                        emitter.send(SseEmitter.event().data(Map.of("delta", delta)));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE发送失败", e);
                    }
                });
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 流式处理失败", e);
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }
}
