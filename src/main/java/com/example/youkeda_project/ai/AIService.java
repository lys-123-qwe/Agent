package com.example.youkeda_project.ai;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final String MODEL = "deepseek-chat";
    private static final String QWEN_VL_MODEL = "qwen-vl-plus";
    private static final int MAX_HISTORY = 10;

    private final RestClient restClient;
    private final RestClient visionRestClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    private final Map<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    public AIService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(apiConfig.getAiApiUrl())
                .build();
        this.visionRestClient = RestClient.builder()
                .baseUrl(apiConfig.getVisionApiUrl())
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

            String reply = (String) response.getChoices().get(0).getMessage().content();
            history.add(new ChatMessage("assistant", reply));
            return reply;

        } catch (Exception e) {
            log.error("DeepSeek调用失败", e);
            return "AI 服务异常: " + e.getMessage();
        }
    }

    public String chatWithImage(String userId, String textPrompt, byte[] imageBytes) {
        if (apiConfig.getVisionApiKey() == null || apiConfig.getVisionApiKey().isBlank()) {
            return "错误: 未配置通义千问 VL API Key";
        }

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = detectMimeType(imageBytes);
        String dataUrl = "data:" + mimeType + ";base64," + base64;

        List<Map<String, Object>> contentParts = new ArrayList<>();
        if (textPrompt != null && !textPrompt.isBlank()) {
            contentParts.add(Map.of("type", "text", "text", textPrompt));
        } else {
            contentParts.add(Map.of("type", "text", "text", "请描述这张图片"));
        }
        contentParts.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手，能够识别图片内容。回答简洁。"));
            return list;
        });

        history.add(new ChatMessage("user", contentParts));
        trimHistory(history);

        try {
            ChatRequest request = new ChatRequest(QWEN_VL_MODEL, new ArrayList<>(history));
            String body = objectMapper.writeValueAsString(request);
            log.info("通义千问VL请求: {}", body);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            byte[] responseBytes = visionRestClient.post()
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiConfig.getVisionApiKey())
                    .body(bodyBytes)
                    .retrieve()
                    .body(byte[].class);

            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("通义千问VL响应: {}", responseJson);

            ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);
            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                return "错误: AI 未返回内容";
            }

            String reply = (String) response.getChoices().get(0).getMessage().content();
            history.add(new ChatMessage("assistant", reply));
            return reply;

        } catch (Exception e) {
            log.error("通义千问VL调用失败", e);
            return "图片识别失败: " + e.getMessage();
        }
    }

    private String detectMimeType(byte[] data) {
        if (data.length > 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) return "image/jpeg";
        if (data.length > 3 && data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E) return "image/png";
        if (data.length > 3 && data[0] == (byte) 0x47 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46) return "image/gif";
        if (data.length > 4 && data[0] == (byte) 0x52 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46 && data[3] == (byte) 0x46) return "image/webp";
        return "image/jpeg";
    }

    private void trimHistory(List<ChatMessage> history) {
        if (history.size() > MAX_HISTORY + 1) {
            ChatMessage system = history.get(0);
            List<ChatMessage> recent = new ArrayList<>(
                    history.subList(history.size() - MAX_HISTORY, history.size()));
            history.clear();
            history.add(system);
            history.addAll(recent);
        }
    }

    public void addExchange(String userId, String userMessage, String assistantReply) {
        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手，回答简洁。"));
            return list;
        });
        history.add(new ChatMessage("user", userMessage));
        history.add(new ChatMessage("assistant", assistantReply));
        trimHistory(history);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
    }
}
