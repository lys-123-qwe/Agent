package com.example.youkeda_project.ai;

import com.example.youkeda_project.tool.Tool;
import com.example.youkeda_project.tool.ToolCall;
import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.util.MimeTypes;


import com.example.youkeda_project.config.ApiConfig;
import com.example.youkeda_project.rag.RagService;
import com.example.youkeda_project.service.wechat.ChatHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 对话服务,支持 DeepSeek 纯文本对话,通义千问 VL 图片识别,Function Calling 工具调用.
 * <p>
 * 每个用户维护独立 session(最多 10 轮历史),
 * 请求/响应全部使用 byte[] + UTF-8 编解码,避免 Windows 控制台 GBK 编码导致乱码.
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final String MODEL = "deepseek-v4-pro";
    private static final String QWEN_VL_MODEL = "qwen-vl-plus";
    private static final int MAX_HISTORY = 10;
    /** token 消耗统计: userId -> [promptTokens, completionTokens] */
    private static final Map<String, long[]> TOKEN_USAGE = new ConcurrentHashMap<>();
    /** token 消耗统计: model -> [promptTokens, completionTokens] */
    private static final Map<String, long[]> MODEL_USAGE = new ConcurrentHashMap<>();
    /** 人设: userId -> persona */
    private final Map<String, String> personas = new ConcurrentHashMap<>();

    /** 获取 token 消耗统计（按账户，供 Web 前端展示）。 */
    public Map<String, long[]> getTokenUsage() { return TOKEN_USAGE; }

    /** 获取 token 消耗统计（按模型，供 Web 前端展示）。 */
    public Map<String, long[]> getModelUsage() { return MODEL_USAGE; }

    /** 设置/更新用户人设（Web 配置面板用）。 */
    public void setPersona(String userId, String persona) {
        if (persona != null && !persona.isBlank()) personas.put(userId, persona);
        else personas.remove(userId);
    }

    /** 获取用户人设。 */
    public String getPersona(String userId) { return personas.get(userId); }

    /** 金融分析智能体的系统提示词（skill 路由命中时替代通用 prompt）。 */
    private static String financialSystemPrompt() {
        return """
            你是一位资深金融分析师，拥有15年A股、港股、美股及期货市场分析经验。

            ## 你的职责
            根据用户提供的数据，进行专业分析并给出投资建议。

            ## 分析框架（严格按此顺序输出）

            ### 一、标的概况
            标的名称、代码、当前价格、涨跌幅、成交量

            ### 二、技术面分析
            - 趋势方向（上涨/下跌/震荡）
            - 关键支撑位和阻力位
            - MA均线状态
            - 成交量配合度

            ### 三、基本面分析（如适用）
            - PE/PB估值水平
            - 行业地位和竞争力
            - 营收和利润趋势

            ### 四、风险评估
            - 主要风险点
            - 风险等级（低/中/高）

            ### 五、投资建议
            - 操作建议：买入/持有/卖出/观望
            - 理由（1-2句话）
            - 止损位建议
            - 目标持有周期

            ## 输出格式要求
            1. 用 Markdown 表格和分隔线结构化输出
            2. 每个分析模块不超过3句话
            3. 用数据说话，避免主观臆测
            4. 如数据不足，诚实说明并建议补充查询""";
    }

    /** ThreadLocal 传递当前 userId,供 MemoryTool 等需要用户上下文的地方使用. */
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    /** 获取当前线程正在处理的 userId(供 Tool 调用). */
    public static String getCurrentUserId() {
        return currentUserId.get();
    }

    private final RestClient restClient;
    private final RestClient visionRestClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;
    private final ChatHistoryService chatHistoryService;
    private final RagService ragService;
    private final Map<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aiservice-db");
        t.setDaemon(true);
        return t;
    });

    public AIService(ApiConfig apiConfig, ObjectMapper objectMapper, ChatHistoryService chatHistoryService, RagService ragService) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.chatHistoryService = chatHistoryService;
        this.ragService = ragService;
        this.restClient = RestClient.builder()
                .baseUrl(apiConfig.getAiApiUrl())
                .build();
        this.visionRestClient = RestClient.builder()
                .baseUrl(apiConfig.getVisionApiUrl())
                .build();
    }

    /**
     * 发送消息给 DeepSeek,支持 Function Calling 工具调用.
     * <p>
     * 自动管理 session 历史,处理多轮工具调用循环.
     * 返回 ChatResult,包含文本回复和工具产生的媒体数据.
     *
     * @param tools 可用的工具列表,可为 null 或空(不使用 Function Calling)
     */
    public ChatResult chat(String userId, String userMessage, List<Tool> tools, String ragContext) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return new ChatResult("错误: 未配置 DeepSeek API Key");
        }

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            String basePrompt = buildBasePrompt(userId);
            // 仅金融 skill 有知识文档，故 ragContext 非空即金融场景，叠加金融分析师提示词
            String enrichedBase = ragContext != null && !ragContext.isBlank()
                ? financialSystemPrompt() + "\n\n" + basePrompt + "\n\n" + ragContext
                : basePrompt;
            // RAG: 根据用户问题检索相关知识并拼入 system prompt
            list.add(new ChatMessage("system", ragService.augment(userMessage, enrichedBase)));
            return list;
        });

        history.add(new ChatMessage("user", userMessage));
        persistMessage(userId, "user", userMessage);
        trimHistory(history);

        currentUserId.set(userId);
        try {
            // 收集本轮所有工具执行产生的媒体结果(图片URL,音频等)
            List<ToolResult> allPendingMedia = new ArrayList<>();
            // 已出图的标的 -> 出图工具名(拦截重复出图)
            Map<String, String> chartedStocks = new HashMap<>();
            // 工具失败追踪:同一工具连续失败≥2次后,跳过调用并通知模型放弃重试
            Map<String, Integer> toolFailureCount = new HashMap<>();
            int maxRounds = apiConfig.getAiToolMaxRounds();

            for (int round = 0; round < maxRounds; round++) {
                List<ChatMessage> filteredHistory = filterForTextOnly(history);

                ChatRequest request = tools != null && !tools.isEmpty()
                    ? new ChatRequest(MODEL, filteredHistory, tools.stream().map(Tool::toToolDefinition).toList())
                    : new ChatRequest(MODEL, filteredHistory);

                String body = objectMapper.writeValueAsString(request);
                log.debug("DeepSeek请求(round={}): {} chars, tools={}", round, body.length(),
                    tools != null ? tools.size() : 0);

                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                ChatResponse response = callCompletion(restClient, apiConfig.getAiApiKey(), bodyBytes);
                if (response == null) {
                    return new ChatResult("错误: AI 服务返回空响应");
                }
                if (response.getChoices() == null || response.getChoices().isEmpty()) {
                    return new ChatResult("错误: AI 未返回内容");
                }

                ChatChoice choice = response.getChoices().get(0);
                if (choice == null || choice.getMessage() == null) {
                    return new ChatResult("错误: AI 返回异常");
                }

                if (choice.isToolCall()) {
                    List<ToolCall> calls = ToolCall.fromChoice(choice);
                    log.debug("DeepSeek 请求工具调用: {}", calls);

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
                    persistMessage(userId, "assistant", "[工具调用]");
                    // 执行每个工具调用,收集结果和媒体
                    for (ToolCall tc : calls) {
                        Tool matched = findTool(tc.functionName(), tools);
                        if (matched == null) {
                            log.warn("未找到工具: {}", tc.functionName());
                            history.add(ChatMessage.tool(tc.id(), tc.functionName(), "错误: 未知工具 " + tc.functionName()));
                            continue;
                        }

                        // 如果该工具已连续失败≥2次,跳过执行并通知模型放弃重试
                        int fails = toolFailureCount.getOrDefault(tc.functionName(), 0);
                        if (fails >= 2) {
                            log.warn("工具 {} 已连续失败 {} 次,跳过执行", tc.functionName(), fails);
                            history.add(ChatMessage.tool(tc.id(), tc.functionName(),
                                "系统提示:工具[" + tc.functionName() + "]之前已调用两次均失败,请不要再尝试使用此工具,直接用文本回复用户."));
                            continue;
                        }

                        Map<String, Object> args = parseArguments(tc.arguments(), userId);
                        log.debug("执行工具: {}({})", tc.functionName(), args);

                        // 同一标的已出过图时,拦截重复出图工具(analyze_stock/generate_chart 都会带图)
                        if (tc.functionName().equals("generate_chart") || tc.functionName().equals("analyze_stock")) {
                            Object stockArg = args.get("stock");
                            String stock = stockArg != null ? stockArg.toString() : "";
                            String prevChartTool = chartedStocks.get(stock);
                            if (prevChartTool != null && !prevChartTool.equals(tc.functionName())) {
                                log.info("标的 {} 已通过 {} 出图,跳过重复出图工具 {}", stock, prevChartTool, tc.functionName());
                                history.add(ChatMessage.tool(tc.id(), tc.functionName(),
                                    "系统提示:" + stock + "的图表已通过" + prevChartTool + "生成并发送给用户,不要重复生成图表,请直接基于已有图表和数据分析总结回复."));
                                continue;
                            }
                        }

                        ToolResult toolResult = matched.execute(args);
                        log.debug("工具结果: {} -> {}, hasImage={}, hasAudio={}",
                            tc.functionName(), toolResult.getText(), toolResult.hasImage(), toolResult.hasAudio());

                        // 检测工具执行结果是否表示失败,失败则递增追踪计数
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
                        // 记录已出图的标的
                        if (toolResult.hasImage()
                                && (tc.functionName().equals("generate_chart") || tc.functionName().equals("analyze_stock"))) {
                            Object stockArg = args.get("stock");
                            chartedStocks.put(stockArg != null ? stockArg.toString() : "", tc.functionName());
                        }

                        // 将文本结果加入对话历史（供 LLM 下一轮使用）
                        String toolTextForAi = resultText;
                        if (toolResult.hasImage()) {
                            toolTextForAi += "\n（系统：图片已生成并发送到微信，回复时可以提及上面这张图）";
                        }
                        history.add(ChatMessage.tool(tc.id(), tc.functionName(), toolTextForAi));
                        persistMessage(userId, "tool", "[" + tc.functionName() + "] " + resultText);
                    }
                    trimHistory(history);
                    continue;
                }

                // 正常文本回复
                Object content = choice.getMessage().content();
                String reply = content instanceof String ? (String) content : "";
                history.add(new ChatMessage("assistant", reply));
                persistMessage(userId, "assistant", reply);

                // 收集本轮所有工具产生的媒体,返回给调用方处理
                return allPendingMedia.isEmpty()
                    ? new ChatResult(reply)
                    : new ChatResult(reply, allPendingMedia);
            }

            log.warn("Function Calling 超过最大轮数({})", maxRounds);
            return new ChatResult("抱歉,工具调用次数过多,请重新提问");

        } catch (Exception e) {
            log.error("DeepSeek调用失败", e);
            if (e.getMessage() != null && e.getMessage().contains("image_url")) {
                return new ChatResult("抱歉,对话历史中包含不支持的内容类型,请发送 /clear 清除对话历史后重试");
            }
            if (e.getMessage() != null && e.getMessage().contains("400")) {
                return new ChatResult("抱歉,AI 服务请求失败,请稍后再试");
            }
            return new ChatResult("抱歉,AI 服务暂时不可用,请稍后再试");
        } finally {
            currentUserId.remove();
        }
    }

    /**
     * 不带 RAG 上下文的兼容接口.
     */
    public ChatResult chat(String userId, String userMessage, List<Tool> tools) {
        return chat(userId, userMessage, tools, null);
    }

    /**
     * 流式对话（供 Web 前端 SSE 使用）：
     * 调用 DeepSeek stream=true，逐段把生成的文本通过 onDelta 回调吐出；
     * 支持 Function Calling 工具循环（与微信侧一致），工具生成的图片通过 onImage 以 base64 dataURL 吐出。
     */
    public String chatStream(String userId, String userMessage, String model, List<Tool> tools,
                             java.util.function.Consumer<String> onDelta,
                             java.util.function.Consumer<String> onImage,
                             java.util.function.Consumer<Map<String, Object>> onLog) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return "错误: 未配置 DeepSeek API Key";
        }

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", buildBasePrompt(userId)));
            return list;
        });
        history.add(new ChatMessage("user", userMessage));
        trimHistory(history);

        // RAG 增强 system prompt：基于当前问题检索知识库拼入（与微信聊天一致）
        if (!history.isEmpty() && "system".equals(history.get(0).role())) {
            String enhanced = ragService.augment(userMessage, String.valueOf(history.get(0).content()));
            String persona = personas.get(userId);
            if (persona != null && !persona.isBlank()) {
                enhanced += "\n\n【用户设定的人设，请严格遵守】" + persona;
            }
            history.set(0, new ChatMessage("system", enhanced));
        }

        StringBuilder full = new StringBuilder();
        currentUserId.set(userId);
        final String usedModel = (model != null && !model.isBlank()) ? model : MODEL;
        try {
            int maxRounds = apiConfig.getAiToolMaxRounds();
            for (int round = 0; round < maxRounds; round++) {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model", usedModel);
                requestBody.put("messages", history);
                requestBody.put("stream", true);
                if (tools != null && !tools.isEmpty()) {
                    requestBody.put("tools", tools.stream().map(Tool::toToolDefinition).toList());
                }
                String body = objectMapper.writeValueAsString(requestBody);
                log.debug("DeepSeek流式请求(round={}): {} chars", round, body.length());

                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    URI.create(apiConfig.getAiApiUrl()).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + apiConfig.getAiApiKey());
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(120000);
                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                conn.getOutputStream().flush();

                // 运行日志：本轮请求
                if (onLog != null) {
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("kind", "req");
                    lm.put("model", usedModel);
                    lm.put("msg", "发送请求给 " + usedModel);
                    lm.put("detail", "第 " + (round + 1) + " 轮 · " + (tools != null && !tools.isEmpty() ? "携带 " + tools.size() + " 个工具" : "纯文本对话"));
                    lm.put("status", "RUN");
                    onLog.accept(lm);
                }

                // 累积流式工具调用片段(按 index)
                List<String[]> pendingCalls = new ArrayList<>();
                StringBuilder roundText = new StringBuilder();

                try (var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) break;
                        JsonNode node = objectMapper.readTree(data);
                        // 统计 token 消耗（流式结束时 usage 字段出现）
                        JsonNode usageNode = node.path("usage");
                        if (usageNode.isObject() && (usageNode.has("prompt_tokens") || usageNode.has("completion_tokens"))) {
                            long pt = usageNode.path("prompt_tokens").asLong(0);
                            long ct = usageNode.path("completion_tokens").asLong(0);
                            if (pt > 0 || ct > 0) {
                                long[] u = TOKEN_USAGE.computeIfAbsent(userId, k -> new long[2]);
                                u[0] += pt;
                                u[1] += ct;
                                long[] um = MODEL_USAGE.computeIfAbsent(usedModel, k -> new long[2]);
                                um[0] += pt;
                                um[1] += ct;
                            }
                        }
                        JsonNode delta = node.path("choices").path(0).path("delta");
                        JsonNode contentNode = delta.path("content");
                        if (contentNode.isTextual()) {
                            String d = contentNode.asText();
                            roundText.append(d);
                            full.append(d);
                            if (onDelta != null) onDelta.accept(d);
                        }
                        JsonNode tcs = delta.path("tool_calls");
                        if (tcs.isArray()) {
                            for (JsonNode t : tcs) {
                                int idx = t.path("index").asInt(0);
                                while (pendingCalls.size() <= idx) pendingCalls.add(new String[3]);
                                if (t.path("id").isTextual()) pendingCalls.get(idx)[0] = t.path("id").asText();
                                JsonNode fn = t.path("function");
                                if (fn.path("name").isTextual()) pendingCalls.get(idx)[1] = fn.path("name").asText();
                                if (fn.path("arguments").isTextual()) {
                                    if (pendingCalls.get(idx)[2] == null) pendingCalls.get(idx)[2] = "";
                                    pendingCalls.get(idx)[2] += fn.path("arguments").asText();
                                }
                            }
                        }
                    }
                }
                conn.disconnect();

                // 无工具调用:本轮文本回复结束
                if (pendingCalls.isEmpty()) {
                    history.add(new ChatMessage("assistant", roundText.toString()));
                    trimHistory(history);
                    if (onLog != null) {
                        Map<String, Object> lm = new LinkedHashMap<>();
                        lm.put("kind", "reply");
                        lm.put("model", usedModel);
                        lm.put("msg", "AI 回复生成完成");
                        lm.put("detail", roundText.length() > 60 ? roundText.substring(0, 60) + "…" : roundText.toString());
                        lm.put("status", "OK");
                        onLog.accept(lm);
                    }
                    return full.toString();
                }

                // 记录 assistant 的工具调用消息
                List<Map<String, Object>> rawToolCalls = new ArrayList<>();
                for (String[] c : pendingCalls) {
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("id", c[0]);
                    raw.put("type", "function");
                    Map<String, Object> func = new LinkedHashMap<>();
                    func.put("name", c[1]);
                    func.put("arguments", c[2]);
                    raw.put("function", func);
                    rawToolCalls.add(raw);
                }
                history.add(ChatMessage.assistantWithToolCalls(rawToolCalls));

                // 执行工具:文本结果回填,图片经 onImage 传给前端
                for (String[] c : pendingCalls) {
                    Tool matched = findTool(c[1], tools);
                    if (matched == null) {
                        log.warn("未找到工具: {}", c[1]);
                        history.add(ChatMessage.tool(c[0], c[1], "错误: 未知工具 " + c[1]));
                        continue;
                    }
                    Map<String, Object> args = parseArguments(c[2], userId);
                    ToolResult toolResult = matched.execute(args);
                    String resultText = toolResult.getText();
                    boolean ok = resultText != null && !resultText.startsWith("错误:");
                    if (onLog != null) {
                        Map<String, Object> lm = new LinkedHashMap<>();
                        lm.put("kind", "tool");
                        lm.put("model", usedModel);
                        lm.put("msg", "调用工具 " + c[1]);
                        lm.put("detail", resultText != null
                            ? (resultText.length() > 60 ? resultText.substring(0, 60) + "…" : resultText)
                            : "");
                        lm.put("status", ok ? "OK" : "FAIL");
                        onLog.accept(lm);
                    }
                    if (toolResult.hasImage() && onImage != null) {
                        if (toolResult.getImageUrl() != null && !toolResult.getImageUrl().isBlank()) {
                            onImage.accept(toolResult.getImageUrl());
                        }
                        if (toolResult.getImageBytes() != null && toolResult.getImageBytes().length > 0) {
                            onImage.accept("data:image/png;base64,"
                                + Base64.getEncoder().encodeToString(toolResult.getImageBytes()));
                        }
                        if (toolResult.getImageBytesList() != null) {
                            for (byte[] img : toolResult.getImageBytesList()) {
                                if (img != null && img.length > 0) {
                                    onImage.accept("data:image/png;base64,"
                                        + Base64.getEncoder().encodeToString(img));
                                }
                            }
                        }
                    }
                    history.add(ChatMessage.tool(c[0], c[1], toolResult.getText()));
                }
                trimHistory(history);
            }
            return full.toString();
        } catch (Exception e) {
            log.error("DeepSeek流式调用失败", e);
            if (onLog != null) {
                Map<String, Object> lm = new LinkedHashMap<>();
                lm.put("kind", "error");
                lm.put("model", usedModel);
                lm.put("msg", "请求失败");
                lm.put("detail", e.getMessage() != null ? e.getMessage() : "未知错误");
                lm.put("status", "FAIL");
                onLog.accept(lm);
            }
            return "错误: AI 服务暂时不可用 - " + e.getMessage();
        } finally {
            currentUserId.remove();
        }
    }

    /**
     * 发送图片给通义千问 VL 进行识别.
     */
    /** 图片识别/问答：把图片字节 + 文字问题发给通义千问 VL 视觉模型，返回识别结果。 */
    public String chatWithImage(String userId, String textPrompt, byte[] imageBytes) {
        if (apiConfig.getVisionApiKey() == null || apiConfig.getVisionApiKey().isBlank()) {
            return "错误: 未配置通义千问 VL API Key";
        }

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = MimeTypes.detect(imageBytes);
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
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手,能够识别图片内容.回答简洁."));
            return list;
        });

        history.add(new ChatMessage("user", contentParts));
        persistMessage(userId, "user", "[图片] " + (textPrompt != null ? textPrompt : "请描述这张图片"));
        trimHistory(history);

        try {
            ChatRequest request = new ChatRequest(QWEN_VL_MODEL, new ArrayList<>(history));
            String body = objectMapper.writeValueAsString(request);
            log.info("通义千问VL请求: {} chars", body.length());

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            ChatResponse response = callCompletion(visionRestClient, apiConfig.getVisionApiKey(), bodyBytes);
            if (response == null) {
                return "错误: AI 服务返回空响应";
            }
            log.info("通义千问VL调用成功");
            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                return "错误: AI 未返回内容";
            }

            String reply = (String) response.getChoices().get(0).getMessage().content();
            history.add(new ChatMessage("assistant", reply));
            persistMessage(userId, "assistant", reply);
            return reply;

        } catch (Exception e) {
            log.error("通义千问VL调用失败", e);
            if (e.getMessage() != null && e.getMessage().contains("400")) {
                return "抱歉,图片识别请求失败,请稍后再试";
            }
            return "抱歉,图片识别服务暂时不可用,请稍后再试";
        }
    }

    /**
     * 带上下文的纯文本回复(不执行工具调用).
     */
    /** 生成回复：把额外上下文（如文件内容）拼进消息，不走 Function Calling。 */
    public String generateWithContext(String userId, String userMessage, String context) {
        if (apiConfig.getAiApiKey() == null || apiConfig.getAiApiKey().isBlank()) {
            return "错误: 未配置 DeepSeek API Key";
        }

        List<ChatMessage> history = sessions.computeIfAbsent(userId, k -> {
            List<ChatMessage> list = new ArrayList<>();
            list.add(new ChatMessage("system", "你是一个 helpful 的 AI 助手,回答简洁."));
            return list;
        });

        String combinedMessage = "用户的问题:" + userMessage + "\n查询结果:" + context
            + "\n请根据查询结果用自然语言回复用户.";
        history.add(new ChatMessage("user", combinedMessage));
        persistMessage(userId, "user", combinedMessage);
        trimHistory(history);

        try {
            List<ChatMessage> filteredHistory = filterForTextOnly(history);
            ChatRequest request = new ChatRequest(MODEL, filteredHistory);
            String body = objectMapper.writeValueAsString(request);
            log.info("上下文回复请求: {} chars", body.length());

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            ChatResponse response = callCompletion(restClient, apiConfig.getAiApiKey(), bodyBytes);
            if (response == null) {
                return "错误: AI 服务返回空响应";
            }
            log.info("上下文回复生成成功");
            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                return "错误: AI 未返回内容";
            }

            String reply = (String) response.getChoices().get(0).getMessage().content();
            history.add(new ChatMessage("assistant", reply));
            persistMessage(userId, "assistant", reply);
            return reply;

        } catch (Exception e) {
            log.error("上下文回复生成失败", e);
            return "抱歉,AI 回复生成失败,请稍后再试";
        }
    }

    /** 构建 AI 系统提示词：基础设定 + 各工具使用规则。 */
    private String buildBasePrompt(String userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"));
        String memoryCtx = buildMemoryContext(userId);
        return "今天是" + today + "。你是一个 helpful 的 AI 助手，回答简洁。"
            + (memoryCtx.isEmpty() ? "" : "\n\n" + memoryCtx)
            + "【重要】只要用户提到任何歌名、歌手、想听歌、找歌，必须调用 music 工具（operation=search）搜索，禁止用你自己的知识回答。"
            + "推荐歌曲必须用 music 工具（operation=recommend）。查资讯和看新闻都用 web_search 工具（不传 keyword 获取今日头条，传 keyword 搜索指定内容，传 category 筛选分类）。"
            + "方言/方言转换/转换方言等必须用 translate 工具（传 dialect 参数），禁止用你自已的知识做方言转换。翻译/文言文/专业术语也用 translate 工具。"
            + "查手机号用 phone_location。用户说朗读/读一下/语音合成时直接用 text_to_speech 工具，不要自己伪造语音链接。"
            + "用户提到网易云热评、热评、来条评论时用 get_hot_comment 工具（不传 song 随机返回，传 song 搜索指定歌曲）。"
            + "用户要查快递、查物流、查包裹时用 query_express 工具（传 number 单号，courier 可选）。"
            + "【重要】如果用户在对话中提到了关于自己的信息（昵称、喜好、职业、所在地等），必须调用 save_user_info 工具保存。"
            + "【重要】工具返回内容中的链接（[详情](url) 或 [来源: XXX](url)）必须原样保留在最终回答中，每条新闻/结果后面必须附带其链接，不得删除或修改。如果工具有返回图片（hasImage=true），说明用户侧已经收到了图片，回复时可以提及上面这张图。"
            + "【市场概况回复格式】调用 market_overview 后：热点板块和汇率直接列数据无需分析，新闻逐条列出发链接（不要忽略工具返回的新闻，也不要自行概括说\u201C无相关新闻\u201D），最后用一两句话总结整体盘面。"
            + "【对比分析回复格式】调用 compare_stocks 后：必须保留评分规则说明原文，不能省略或自行概括。"
            + "用户问对比时优先调用 compare_stocks 工具。"
            + "【大盘/市场行情】用户问'今天市场怎么样''大盘怎么样'等时，优先调用 market_overview 工具获取指数+热点+汇率+财经新闻，不要在总结前省略新闻。"
            + "【免责声明】凡是涉及股票行情、技术分析、投资建议、资金流向等金融类回答，最后必须附上一句免责声明：'⚠️ 以上内容仅供参考，不构成投资建议。'"
            + "【基本面】查股票时若用户问到市盈率/市净率/市值等，如实展示工具返回的数据，数据缺失时不要编造。"
            + "【自选股】用户说'加自选''关注''收藏''添加到自选'等调用 watchlist（operation=add）；"
            + "说'删自选''取消关注''移除'等调用 watchlist（operation=remove）；"
            + "说'自选股''我的自选''关注的股票'等调用 watchlist（operation=list）。"
            + "【持仓】用户说'买入''建仓''加仓'等调用 position（operation=add，传股票、数量、价格）；"
            + "说'卖出''减仓''清仓'等调用 position（operation=remove）；"
            + "说'持仓''我的持仓''盈亏'等调用 position（operation=list）。"
            + "【期货】查期货时：若用户只说了品种名（如'螺纹钢''原油'）或想了解有哪些合约，调用 get_futures 传品种名，工具会返回该品种合约列表（含主力/次主力），你需原样展示并询问用户想查哪个合约；"
            + "用户回复具体合约代码（如'RB2610'）后，再调用 get_futures 传该代码查看行情；"
            + "若用户明确要当前主力行情（如'螺纹钢现在多少钱'）或已指定合约代码，直接调用 get_futures 传对应代码或连续合约(RB0/CL)即可，不要多此一举弹列表。"
            + "【走势分析】用户说'进一步分析''分析走势''技术分析''怎么看''未来怎么走'等要对某只股票/指数做分析时，调用 analyze_stock（输出技术面分析并自动附带K线图，无需再调用 generate_chart，避免重复出图）。"
            + "若用户只是单纯查行情（'查一下''多少钱'），则只查行情，不要生成图表。"
            + "【数据时效】回答股票/基金/期货行情时必须注意：A股/港股交易日为周一至周五（法定节假日除外），美股为美国时间周一至周五。"
            + "周末或节假日查询到的是上一交易日收盘数据，必须明确告知实际数据日期（如'截至上周五收盘'）；"
            + "周一到周五但尚未开盘（如早8点）说明'截至昨日收盘'；盘中则说'实时行情'。永远不要让用户误以为非今日数据是今天的实时数据。"
            + "【期货合约】期货合约有固定到期日（交割月份），合约到期会强制平仓或进入交割。回复时标注主力合约月份（如'玉米2509合约'）并提醒关注到期时间；"
            + "持仓合约临近最后交易日要警告'该合约即将到期，注意平仓或移仓换月'；分析走势时要说清分析的是哪个合约，不同月份合约价格差异可能较大。";
    }

    /** 从数据库加载用户记忆,构建上下文块. */
    private String buildMemoryContext(String userId) {
        Map<String, List<String>> mem = chatHistoryService.getAllMemory(userId);
        if (mem.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("[以下是关于用户的重要信息,请在对话时自然地引用]\n");
        for (var entry : mem.entrySet()) {
            String key = entry.getKey();
            // 取最新的一条值
            String value = entry.getValue().getLast();
            sb.append("- ").append(translateKey(key)).append(": ").append(value).append("\n");
        }
        return sb.toString();
    }

    /** 将记忆键名（英文 key）翻译成中文显示名。 */
    private static String translateKey(String key) {
        return switch (key) {
            case "nickname" -> "昵称";
            case "food_preference" -> "食物偏好";
            case "location" -> "所在地";
            case "occupation" -> "职业";
            case "hobby" -> "爱好";
            case "pet" -> "宠物";
            case "birthday" -> "生日";
            case "name" -> "姓名";
            default -> key;
        };
    }

    @SuppressWarnings("unchecked")
    /** 解析工具参数 JSON 字符串为 Map，解析失败返回空 Map。 */
    private Map<String, Object> parseArguments(String argumentsJson, String userId) {
        Map<String, Object> args;
        if (argumentsJson == null || argumentsJson.isBlank()) {
            args = new LinkedHashMap<>();
        } else {
            try {
                args = objectMapper.readValue(argumentsJson, LinkedHashMap.class);
            } catch (Exception e) {
                log.warn("解析工具参数失败: {}", argumentsJson, e);
                args = new LinkedHashMap<>();
            }
        }
        args.put("_user_id", userId);
        return args;
    }

    /** 按名称在工具列表中查找工具，未找到返回 null。 */
    private Tool findTool(String name, List<Tool> tools) {
        if (tools == null) return null;
        return tools.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /** 发送一次对话补全请求并解析响应。响应为空返回 null，解析异常向上抛。 */
    private ChatResponse callCompletion(RestClient client, String apiKey, byte[] bodyBytes) throws Exception {
        byte[] responseBytes = client.post()
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .body(bodyBytes)
                .retrieve()
                .body(byte[].class);
        if (responseBytes == null) return null;
        String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
        log.debug("AI 响应: {} chars", responseJson.length());
        return objectMapper.readValue(responseJson, ChatResponse.class);
    }

    /** 裁剪会话历史，只保留最近 MAX_HISTORY 条（控制 token 长度）。 */
    private void trimHistory(List<ChatMessage> history) {
        if (history.size() > MAX_HISTORY + 1) {
            ChatMessage system = history.get(0);
            int total = history.size();
            int start = total - MAX_HISTORY;

            // 如果保留段第一条是 tool 消息,说明它的 assistant(TC) 被裁剪掉了,
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
    /** 过滤历史中仅保留文本消息（工具调用轮次用，避免图片等媒体干扰解析）。 */
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

    // ==================== DB persistence ====================

    /** 异步保存消息到 SQLite. */
    private void persistMessage(String userId, String role, Object content) {
        dbExecutor.submit(() -> {
            try {
                String text = serializeContent(content);
                chatHistoryService.saveMessage(userId, role, text);
            } catch (Exception e) {
                log.error("保存消息到数据库失败: {}", e.getMessage());
            }
        });
    }

    /** 序列化消息内容（对象 → 字符串），供持久化使用。 */
    private static String serializeContent(Object obj) {
        if (obj == null) return "";
        if (obj instanceof String s) return s;
        if (obj instanceof List<?> list && !list.isEmpty()) {
            // 提取 text 部分
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    if ("text".equals(type) && map.get("text") != null) {
                        sb.append(map.get("text"));
                    } else if ("image".equals(type)) {
                        sb.append("[图片]");
                    }
                }
            }
            return sb.toString();
        }
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

}
