package com.example.youkeda_project.ai;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final String MODEL = "deepseek-chat";
    private static final int MAX_HISTORY = 10;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    private final Map<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    public AIService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(apiConfig.getAiApiUrl())
                .build();
    }

    public String chat(String userId, String userMessage) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return "错误: 未配置 DeepSeek API Key";
        }

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手，回答简洁。"));
            return list;
        });

        history.add(new ChatMessage("user", userMessage));
        trimHistory(history);

        try {
            ChatRequest request = new ChatRequest(MODEL, new ArrayList<>(history));
            String body = objectMapper.writeValueAsString(request);
            log.info("DeepSeek请求: {}", body);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            byte[] responseBytes = restClient.post()
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiConfig.getAiApiKey())
                    .body(bodyBytes)
                    .retrieve()
                    .body(byte[].class);

            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("DeepSeek响应: {}", responseJson);

            ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);
            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                return "错误: AI 未返回内容";
            }

            String reply = response.getChoices().get(0).getMessage().getContent();
            history.add(new ChatMessage("assistant", reply));
            return reply;

        } catch (Exception e) {
            log.error("DeepSeek调用失败", e);
            return "AI 服务异常: " + e.getMessage();
        }
    }

    private void trimHistory(List<ChatMessage> history) {
        // 保留 system + 最近 MAX_HISTORY 条对话
        if (history.size() > MAX_HISTORY + 1) {
            ChatMessage system = history.get(0);
            List<ChatMessage> recent = history.subList(history.size() - MAX_HISTORY, history.size());
            history.clear();
            history.add(system);
            history.addAll(recent);
        }
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
    }
}