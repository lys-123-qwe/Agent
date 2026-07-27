package com.example.youkeda_project.ai;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.*;
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
    private static final String MODEL = "deepseek-v4-pro";
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
    public ChatResult chat(String userId, String userMessage, List<Tool> tools) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return new ChatResult("错误: 未配置 DeepSeek API Key");
        }

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"));
            list.add(new ChatMessage("system", "今天是" + today + "。你是一个 helpful 的 AI 助手，回答简洁。【重要】只要用户提到任何歌名、歌手、想听歌、找歌，必须调用 play_song 工具搜索，禁止用你自己的知识回答。推荐歌曲必须用 recommend_music 工具。查资讯和看新闻都用 web_search 工具（不传 keyword 获取今日头条，传 keyword 搜索指定内容，传 category 筛选分类）。方言/方言转换/转换方言等必须用 translate 工具（传 dialect 参数），禁止用你自已的知识做方言转换。翻译/文言文/专业术语也用 translate 工具。查手机号用 phone_location。用户说朗读/读一下/语音合成时直接用 text_to_speech 工具，不要自己伪造语音链接。用户提到网易云热评、热评、来条评论时用 get_hot_comment 工具（不传 song 随机返回，传 song 搜索指定歌曲）。用户要查快递、查物流、查包裹时用 query_express 工具（传 number 单号，courier 可选）。【重要】工具返回内容中的链接（[详情](url) 或 [来源: XXX](url)）必须原样保留在最终回答中，每条新闻/结果后面必须附带其链接，不得删除或修改。"));
            return list;
        });

        history.add(new ChatMessage("user", userMessage));
        trimHistory(history);

        try {
            // 收集本轮所有工具执行产生的媒体结果（图片URL、音频等）
            List<ToolResult> allPendingMedia = new ArrayList<>();
            // 工具失败追踪：同一工具连续失败≥2次后，跳过调用并通知模型放弃重试
            Map<String, Integer> toolFailureCount = new HashMap<>();
            int maxRounds = 8;

            for (int round = 0; round < maxRounds; round++) {
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
                    // 执行每个工具调用，收集结果和媒体
                    for (ToolCall tc : calls) {
                        Tool matched = findTool(tc.functionName(), tools);
                        if (matched == null) {
                            log.warn("未找到工具: {}", tc.functionName());
                            history.add(ChatMessage.tool(tc.id(), tc.functionName(), "错误: 未知工具 " + tc.functionName()));
                            continue;
                        }

                        // 如果该工具已连续失败≥2次，跳过执行并通知模型放弃重试
                        int fails = toolFailureCount.getOrDefault(tc.functionName(), 0);
                        if (fails >= 2) {
                            log.warn("工具 {} 已连续失败 {} 次，跳过执行", tc.functionName(), fails);
                            history.add(ChatMessage.tool(tc.id(), tc.functionName(),
                                "系统提示：工具[" + tc.functionName() + "]之前已调用两次均失败，请不要再尝试使用此工具，直接用文本回复用户。"));
                            continue;
                        }

                        Map<String, Object> args = parseArguments(tc.arguments());
                        log.info("执行工具: {}({})", tc.functionName(), args);

                        ToolResult toolResult = matched.execute(args);
                        log.info("工具结果: {} -> {}, hasImage={}, hasAudio={}",
                            tc.functionName(), toolResult.getText(), toolResult.hasImage(), toolResult.hasAudio());

                        // 检测工具执行结果是否表示失败，失败则递增追踪计数
                        String resultText = toolResult.getText();
                        if (resultText != null && (resultText.startsWith("错误:") || resultText.contains("失败"))) {
                            toolFailureCount.merge(tc.functionName(), 1, Integer::sum);
                            log.warn("工具执行失败: {} -> {} (失败次数: {})",
                                tc.functionName(), resultText, toolFailureCount.get(tc.functionName()));
                        }

                        // 收集媒体结果
                        if (toolResult.hasImage() || toolResult.hasAudio()) {
                            allPendingMedia.add(toolResult);
                        }

                        // 将文本结果加入对话历史（供 LLM 下一轮使用）
                        history.add(ChatMessage.tool(tc.id(), tc.functionName(), resultText));
                    }
                    trimHistory(history);
                    continue;
                }

                // 正常文本回复
                String reply = (String) choice.getMessage().content();
                history.add(new ChatMessage("assistant", reply));

                // 收集本轮所有工具产生的媒体，返回给调用方处理
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
            int total = history.size();
            int start = total - MAX_HISTORY;

            // 如果保留段第一条是 tool 消息，说明它的 assistant(TC) 被裁剪掉了，
            // 往前回溯直到找到对应的 tool_calls 上下文消息
            while (start > 1 && "tool".equals(history.get(start).role())) {
                start--;
            }

            List<ChatMessage> recent = new ArrayList<>(
                    history.subList(start, total));
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
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手，回答简洁。"));
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
