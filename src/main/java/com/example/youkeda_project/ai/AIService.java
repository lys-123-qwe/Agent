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

/**
 * AI 对话服务，支持 DeepSeek 纯文本对话和通义千问 VL 图片识别。
 * <p>
 * 每个用户维护独立 session（最多 10 轮历史），
 * 请求/响应全部使用 byte[] + UTF-8 编解码，避免 Windows 控制台 GBK 编码导致乱码。
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final String MODEL = "deepseek-chat";
    private static final String QWEN_VL_MODEL = "qwen-vl-plus";
    private static final int MAX_HISTORY = 10; // 每个用户保留的最大对话轮数

    private final RestClient restClient;          // 对接 DeepSeek
    private final RestClient visionRestClient;     // 对接通义千问 VL
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    // userId -> 对话历史列表，ConcurrentHashMap 保证线程安全
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

    /**
     * 发送纯文本消息给 DeepSeek，返回 AI 回复。
     * 自动维护用户 session 历史，保留 system prompt 并截断超出部分。
     * 注意：历史记录中可能包含通义千问 VL 的 image_url 内容，
     * DeepSeek 不支持这种格式，需要过滤掉非文本内容。
     */
    public String chat(String userId, String userMessage) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return "错误: 未配置 DeepSeek API Key";
        }

        // computeIfAbsent: 首次访问时创建 session 并插入 system prompt
        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手，回答简洁。"));
            return list;
        });

        history.add(new ChatMessage("user", userMessage));
        trimHistory(history);

        try {
            // 过滤历史记录，只保留纯文本内容（DeepSeek 不支持 image_url）
            List<ChatMessage> filteredHistory = filterForTextOnly(history);
            ChatRequest request = new ChatRequest(MODEL, filteredHistory);
            String body = objectMapper.writeValueAsString(request);
            log.info("DeepSeek请求: {}", body);

            // byte[] 编解码避免 Windows 控制台 GBK 编码对 JSON 的污染
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
            // 返回用户友好的错误提示，技术细节记录在日志中
            if (e.getMessage() != null && e.getMessage().contains("image_url")) {
                return "抱歉，对话历史中包含不支持的内容类型，请发送 /clear 清除对话历史后重试";
            }
            if (e.getMessage() != null && e.getMessage().contains("400")) {
                return "抱歉，AI 服务请求失败，请稍后再试";
            }
            return "抱歉，AI 服务暂时不可用，请稍后再试";
        }
    }

    /**
     * 发送图片（附带文字 prompt）给通义千问 VL 进行识别分析。
     * 图片编码为 base64 data URI 放入多模态 content 数组。
     */
    public String chatWithImage(String userId, String textPrompt, byte[] imageBytes) {
        if (apiConfig.getVisionApiKey() == null || apiConfig.getVisionApiKey().isBlank()) {
            return "错误: 未配置通义千问 VL API Key";
        }

        // 将图片字节转为 base64 data URI，用于多模态 API 的 image_url 字段
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = detectMimeType(imageBytes);
        String dataUrl = "data:" + mimeType + ";base64," + base64;

        // 多模态 content 数组: [{type: "text", text: "..."}, {type: "image_url", image_url: {url: "data:..."}}]
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
            // 返回用户友好的错误提示
            if (e.getMessage() != null && e.getMessage().contains("400")) {
                return "抱歉，图片识别请求失败，请稍后再试";
            }
            return "抱歉，图片识别服务暂时不可用，请稍后再试";
        }
    }

    /**
     * 通过文件魔数（Magic Number）检测图片 MIME 类型，用于构造 data URI。
     * 只检测 JPEG/PNG/GIF/WebP，默认回退 JPEG。
     */
    private String detectMimeType(byte[] data) {
        if (data.length > 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) return "image/jpeg";
        if (data.length > 3 && data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E) return "image/png";
        if (data.length > 3 && data[0] == (byte) 0x47 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46) return "image/gif";
        if (data.length > 4 && data[0] == (byte) 0x52 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46 && data[3] == (byte) 0x46) return "image/webp";
        return "image/jpeg";
    }

    /**
     * 截断对话历史，保留 system prompt 和最近的 MAX_HISTORY 轮对话。
     * 注意: subList 返回的是视图，必须 new ArrayList<>(...) 复制后再操作，否则会抛 ConcurrentModificationException。
     */
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

    /**
     * 过滤对话历史，只保留纯文本内容供 DeepSeek 使用。
     * 通义千问 VL 的图片消息包含 image_url 内容，DeepSeek 不支持。
     * 解决方案：将多模态内容转换为纯文本（只保留文字部分）。
     */
    @SuppressWarnings("unchecked")
    private List<ChatMessage> filterForTextOnly(List<ChatMessage> history) {
        List<ChatMessage> filtered = new ArrayList<>();
        for (ChatMessage msg : history) {
            Object content = msg.content();
            if (content instanceof String) {
                // 纯文本消息，直接保留
                filtered.add(msg);
            } else if (content instanceof List<?> parts) {
                // 多模态消息（来自通义千问 VL），提取文字部分
                StringBuilder textContent = new StringBuilder();
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> map) {
                        Object type = map.get("type");
                        if ("text".equals(type) && map.get("text") != null) {
                            if (!textContent.isEmpty()) textContent.append("\n");
                            textContent.append(map.get("text"));
                        }
                    }
                }
                if (!textContent.isEmpty()) {
                    filtered.add(new ChatMessage(msg.role(), textContent.toString()));
                }
                // 如果没有文字内容，跳过这条消息（避免发送空消息给 DeepSeek）
            } else {
                // 未知类型，跳过
                log.warn("跳过未知消息内容类型: {}", content != null ? content.getClass().getSimpleName() : "null");
            }
        }
        return filtered;
    }

    /**
     * 手动向 session 注入一组对话（用于天气等非 AI 回复也进入上下文）。
     */
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

    public boolean hasSession(String userId) {
        return sessions.containsKey(userId);
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
    }
}
