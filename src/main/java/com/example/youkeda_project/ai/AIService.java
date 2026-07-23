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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 对话服务，支持 DeepSeek 纯文本对话、通义千问 VL 图片识别、Function Calling 工具调用。
 * <p>
 * 每个用户维护独立 session（最多 10 轮历史），
 * 请求/响应全部使用 byte[] + UTF-8 编解码，避免 Windows 控制台 GBK 编码导致乱码。
 */
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

    /**
     * 发送消息给 DeepSeek，支持 Function Calling 工具调用。
     * <p>
     * 自动管理 session 历史，处理多轮工具调用循环。
     * 返回 ChatResult，包含文本回复和工具产生的媒体数据。
     *
     * @param tools 可用的工具列表，可为 null 或空（不使用 Function Calling）
     */
    /**
     * 多工具编排对话。
     * <p>
     * 调用模型：
     * <pre>
     * Round 1: DeepSeek 返回 [toolA, toolB] (同轮并行)
     *   → 去重检查，跳过已调工具
     *   → 顺序执行每个工具，结果写入 history + 媒体收集
     * Round 2: DeepSeek 看到所有结果，或继续调新工具（串行链式），或返回文本
     * Round N: 最多 5 轮
     * </pre>
     * 数据返回路径：
     * <pre>
     * Tool.execute() → ToolResult{text, imageUrl, audioBytes}
     *   → text → ChatMessage.tool() → 给 DeepSeek 下轮参考
     *   → imageUrl/audioBytes → allPendingMedia → ChatResult
     *     → IlinkService: CDN上传 → 微信
     * </pre>
     */
    public ChatResult chat(String userId, String userMessage, List<Tool> tools) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return new ChatResult("错误: 未配置 DeepSeek API Key");
        }

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", """
                你是一个 helpful 的 AI 助手，擅长组合使用多个工具完成任务。
                当用户指令涉及多个需求时，你应该在同一次回复中调用多个不同的工具来一次性完成。
                例如：
                - 查天气并读出来 → 同时调用 get_weather + text_to_speech
                - 搜索新闻并播报 → 同时调用 web_search + text_to_speech
                - 查多个城市天气 → 同时调用多次 get_weather
                每次调用都使用不同的工具，工具返回中的链接必须完整保留在回答中。"""));
            return list;
        });

        history.add(new ChatMessage("user", userMessage));
        trimHistory(history);

        try {
            List<ToolResult> allPendingMedia = new ArrayList<>();
            int maxRounds = 5;

            for (int round = 0; round < maxRounds; round++) {
                java.util.Set<String> roundTools = new java.util.LinkedHashSet<>();
                List<ChatMessage> filteredHistory = filterForTextOnly(history);

                ChatRequest request = tools != null && !tools.isEmpty()
                    ? new ChatRequest(MODEL, filteredHistory, tools.stream().map(Tool::toToolDefinition).toList())
                    : new ChatRequest(MODEL, filteredHistory);

                String body = objectMapper.writeValueAsString(request);
                log.info("DeepSeek请求(round={}): {}", round, body);

                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                byte[] responseBytes = restClient.post()
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("Authorization", "Bearer " + apiConfig.getAiApiKey())
                        .body(bodyBytes)
                        .retrieve()
                        .body(byte[].class);

                String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
                log.info("DeepSeek响应(round={}): {}", round, responseJson);

                ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);
                if (response.getChoices() == null || response.getChoices().isEmpty()) {
                    return new ChatResult("错误: AI 未返回内容");
                }

                ChatChoice choice = response.getChoices().get(0);

                if (choice.isToolCall()) {
                    List<ToolCall> calls = ToolCall.fromChoice(choice);
                    log.info("DeepSeek 请求工具调用: {}", calls);

                    // 构建 tool_calls 结构加入历史
                    List<Map<String, Object>> rawToolCalls = new ArrayList<>();
                    for (ToolCall tc : calls) {
                        Map<String, Object> raw = new LinkedHashMap<>();
                        raw.put("id", tc.id());
                        raw.put("type", "function");
                        Map<String, Object> func = new LinkedHashMap<>();
                        func.put("name", tc.functionName());
                        func.put("arguments", tc.arguments());
                        raw.put("function", func);
                        rawToolCalls.add(raw);
                    }
                    history.add(ChatMessage.assistantWithToolCalls(rawToolCalls));

                    for (ToolCall tc : calls) {
                        // 去重：同一轮并行调用中同工具只调一次（跨轮允许不同参数重试）
                        if (!roundTools.add(tc.functionName())) {
                            String dupMsg = "工具 " + tc.functionName() + " 已在本次指令中调用过，结果见上，不再重复执行";
                            log.info("跳过重复工具: {}", tc.functionName());
                            history.add(ChatMessage.tool(tc.id(), tc.functionName(), dupMsg));
                            continue;
                        }

                        Tool matched = findTool(tc.functionName(), tools);
                        if (matched == null) {
                            log.warn("未找到工具: {}", tc.functionName());
                            history.add(ChatMessage.tool(tc.id(), tc.functionName(), "错误: 未知工具 " + tc.functionName()));
                            continue;
                        }

                        Map<String, Object> args = parseArguments(tc.arguments());
                        log.info("执行工具: {}({})", tc.functionName(), args);

                        ToolResult toolResult = matched.execute(args);
                        log.info("工具结果: {} -> {}, hasImage={}, hasAudio={}",
                            tc.functionName(), toolResult.getText(), toolResult.hasImage(), toolResult.hasAudio());

                        if (toolResult.hasImage() || toolResult.hasAudio()) {
                            allPendingMedia.add(toolResult);
                        }

                        history.add(ChatMessage.tool(tc.id(), tc.functionName(), toolResult.getText()));
                    }
                    trimHistory(history);
                    continue;
                }

                String reply = (String) choice.getMessage().content();
                history.add(new ChatMessage("assistant", reply));

                return allPendingMedia.isEmpty()
                    ? new ChatResult(reply)
                    : new ChatResult(reply, allPendingMedia);
            }

            log.warn("Function Calling 超过最大轮数({})", maxRounds);
            return new ChatResult("抱歉，工具调用次数过多，请重新提问");

        } catch (Exception e) {
            log.error("DeepSeek调用失败", e);
            if (e.getMessage() != null && e.getMessage().contains("image_url")) {
                return new ChatResult("抱歉，对话历史中包含不支持的内容类型，请发送 /clear 清除对话历史后重试");
            }
            if (e.getMessage() != null && e.getMessage().contains("400")) {
                return new ChatResult("抱歉，AI 服务请求失败，请稍后再试");
            }
            return new ChatResult("抱歉，AI 服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 纯文本对话，不使用工具（向后兼容）。
     */
    public String chat(String userId, String userMessage) {
        return chat(userId, userMessage, null).getReplyText();
    }

    /**
     * 发送图片给通义千问 VL 进行识别。
     */
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
        contentParts.add(Map.of("type", "image", "image", dataUrl));

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", """
                你是一个新闻助手。当工具返回搜索结果时，先判断哪些结果与用户问题相关，过滤无关内容，然后用自然语言回答用户。工具返回中的链接必须完整保留在回答中，不要省略或修改。"""));
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
            if (e.getMessage() != null && e.getMessage().contains("400")) {
                return "抱歉，图片识别请求失败，请稍后再试";
            }
            return "抱歉，图片识别服务暂时不可用，请稍后再试";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, LinkedHashMap.class);
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", argumentsJson, e);
            return Map.of();
        }
    }

    private Tool findTool(String name, List<Tool> tools) {
        if (tools == null) return null;
        return tools.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElse(null);
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

    @SuppressWarnings("unchecked")
    private List<ChatMessage> filterForTextOnly(List<ChatMessage> history) {
        List<ChatMessage> filtered = new ArrayList<>();
        for (ChatMessage msg : history) {
            Object content = msg.content();
            if (content instanceof String) {
                filtered.add(msg);
            } else if (content instanceof List<?> parts) {
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
                    filtered.add(new ChatMessage(msg.role(), textContent.toString(), msg.toolCallId(), msg.name(), null));
                }
            } else if (content == null) {
                filtered.add(msg);
            } else {
                log.warn("跳过未知消息内容类型: {}", content.getClass().getSimpleName());
            }
        }
        return filtered;
    }

    public String generateWithContext(String userId, String userMessage, String context) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return "错误: 未配置 DeepSeek API Key";
        }

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手，回答简洁。当使用工具获取到搜索结果时，先判断哪些结果与用户问题相关，过滤无关内容，然后用自然语言回答用户。工具返回中的链接必须完整保留在回答中，不要省略或修改。"));
            return list;
        });

        String combinedMessage = "用户的问题：" + userMessage + "\n查询结果：" + context
            + "\n请根据查询结果用自然语言回复用户。";
        history.add(new ChatMessage("user", combinedMessage));
        trimHistory(history);

        try {
            List<ChatMessage> filteredHistory = filterForTextOnly(history);
            ChatRequest request = new ChatRequest(MODEL, filteredHistory);
            String body = objectMapper.writeValueAsString(request);
            log.info("上下文回复请求: {}", body);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            byte[] responseBytes = restClient.post()
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiConfig.getAiApiKey())
                    .body(bodyBytes)
                    .retrieve()
                    .body(byte[].class);

            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("上下文回复响应: {}", responseJson);

            ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);
            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                return "错误: AI 未返回内容";
            }

            String reply = (String) response.getChoices().get(0).getMessage().content();
            history.add(new ChatMessage("assistant", reply));
            return reply;

        } catch (Exception e) {
            log.error("上下文回复生成失败", e);
            return "抱歉，AI 回复生成失败，请稍后再试";
        }
    }

    public void addExchange(String userId, String userMessage, String assistantReply) {
        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手，回答简洁。工具返回的内容中如有链接，请完整保留并输出给用户。"));
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
