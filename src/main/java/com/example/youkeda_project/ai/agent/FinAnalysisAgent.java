package com.example.youkeda_project.ai.agent;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.ChatMessage;
import com.example.youkeda_project.ai.ChatRequest;
import com.example.youkeda_project.ai.ChatResponse;
import com.example.youkeda_project.config.ApiConfig;
import com.example.youkeda_project.service.stock.FinDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金融分析引擎。
 * <p>
 * 核心流程：意图解析 → 数据采集 → Prompt 组装 → AI 推理 → 返回分析报告。
 * <p>
 * 支持的分析类型见 {@link AnalysisType}，包括个股分析、基金分析、期货趋势、
 * 板块产业链分析、跨市场联动、持仓诊断、资产配置、大盘综评等。
 */

@Service
public class FinAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(FinAnalysisAgent.class);

    /** 意图解析用的轻量模型（不占用主 session） */
    private static final String INTENT_MODEL = "deepseek-chat";

    /** 统一免责声明 */
    private static final String DISCLAIMER = "\n\n---\n⚠️ 免责声明：以上分析仅供参考，不构成投资建议。市场有风险，投资须谨慎。";
    private static final String DISCLAIMER_SHORT = "免责声明";

    private final RestClient intentClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;
    private final AIService aiService;
    private final FinDataService finDataService;

    public FinAnalysisAgent(ApiConfig apiConfig,
                            ObjectMapper objectMapper,
                            AIService aiService,
                            FinDataService finDataService) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.aiService = aiService;
        this.finDataService = finDataService;
        this.intentClient = RestClient.builder()
                .baseUrl(apiConfig.getAiApiUrl())
                .build();
    }

    /**
     * 执行金融分析。
     *
     * @param userId 用户 ID（用于 session 管理）
     * @param query  用户原始问题
     * @return 分析报告文本
     */
    public String analyze(String userId, String query) {
        long t0 = System.currentTimeMillis();
        log.info("金融分析开始: userId={}, query={}", userId, query);

        try {
            // Step 1: 意图解析
            AnalysisIntent intent = parseIntent(query);
            log.info("意图解析结果: {}", intent);

            // Step 2: 数据采集
            FinDataBundle data = finDataService.collect(intent);
            if (data.isEmpty()) {
                return "抱歉，未能获取到相关金融数据。请检查品种名称是否正确，或稍后重试。";
            }

            // Step 3: 构建分析 prompt + 数据上下文
            String systemPrompt = buildSystemPrompt(intent.getType());
            String dataContext = data.toContextText();

            // Step 4: AI 推理（独立 DeepSeek 调用，不污染 Function Calling session）
            // 结果通过 Tool Result 通道返回给 DeepSeek，由 DeepSeek 在下一轮呈现给用户
            String userMessage = buildAnalysisRequest(query, intent, dataContext);
            String result = callDeepSeek(systemPrompt, userMessage, "deepseek-v4-pro");

            // 兜底：确保免责声明一定存在
            result = ensureDisclaimer(result);

            long elapsed = System.currentTimeMillis() - t0;
            log.info("金融分析完成: {}ms, 报告长度={}字符", elapsed, result != null ? result.length() : 0);
            return result;

        } catch (Exception e) {
            log.error("金融分析失败", e);
            return "抱歉，金融分析服务暂时不可用，请稍后再试。\n错误信息: " + e.getMessage();
        }
    }

    // ==================== 意图解析 ====================

    /**
     * 使用 DeepSeek 轻量调用解析用户意图。
     * 不占用用户主 session，独立的一次性调用。
     */
    private AnalysisIntent parseIntent(String query) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));

        String systemPrompt = """
            你是金融意图解析器。分析用户问题，输出JSON参数。

            ## 类型判断规则（type）:
            - 大盘/市场整体 → MARKET_OVERVIEW，indices默认["上证指数","深证成指","创业板指"]
            - 具体板块/产业链 → SECTOR_ANALYSIS，stocks/futures填该板块核心品种
            - 2个以上品种需联动分析 → CROSS_MARKET
            - 用户列出持仓/我的组合 → PORTFOLIO_DIAGNOSIS
            - 单个股票 → STOCK_ANALYSIS，单个基金 → FUND_ANALYSIS
            - 单个期货 → FUTURES_TREND
            - 两个基金/ETF对比 → ETF_VS_FUND
            - 资产怎么配/股债比例 → ASSET_ALLOCATION
            - 新闻/政策影响 → NEWS_IMPACT
            - 无法确定 → GENERAL

            ## 品种识别示例:
            - "新能源"/"光伏"/"锂电" → stocks:["宁德时代","比亚迪","隆基绿能"], futures:["碳酸锂"], funds:["新能源ETF"]
            - "半导体"/"芯片" → stocks:["中芯国际","寒武纪"], funds:["半导体ETF","芯片ETF"]
            - "白酒"/"消费" → stocks:["贵州茅台","五粮液"], funds:["酒ETF","消费ETF"]
            - "AI"/"人工智能" → stocks:["寒武纪","海康威视"], funds:["人工智能ETF"]
            - "原油"/"黄金" → futures:["原油","黄金"]
            - "我的持仓:茅台+沪深300ETF" → stocks:["贵州茅台"], funds:["沪深300ETF"]

            ## 今天: %s

            ## 输出（只输出JSON）:
            {"type":"","stocks":[],"futures":[],"funds":[],"indices":[],"currencyPair":null,"newsKeyword":null,"timeframe":"中期","needsMoneyFlow":false}
            """.formatted(today);

        try {
            String result = callDeepSeek(systemPrompt, query, INTENT_MODEL);
            // 提取 JSON（可能包裹在 ```json ... ``` 中）
            String json = extractJson(result);
            return parseIntentJson(json, query);
        } catch (Exception e) {
            log.warn("意图解析失败，回退到通用分析: {}", e.getMessage());
            return fallbackIntent(query);
        }
    }

    /** 调用 DeepSeek，返回文本。独立于主 AIService，不污染用户 session。 */
    private String callDeepSeek(String systemPrompt, String userMessage, String model) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", systemPrompt));
            messages.add(new ChatMessage("user", userMessage));

            ChatRequest request = new ChatRequest(model, messages);

            String body = objectMapper.writeValueAsString(request);
            log.debug("意图解析请求: {}", body);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            byte[] responseBytes = intentClient.post()
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiConfig.getAiApiKey())
                    .body(bodyBytes)
                    .retrieve()
                    .body(byte[].class);

            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.debug("意图解析响应: {}", responseJson);

            // 统计 token 消耗
            JsonNode usageNode = objectMapper.readTree(responseJson).path("usage");
            if (usageNode.isObject()) {
                long pt = usageNode.path("prompt_tokens").asLong(0);
                long ct = usageNode.path("completion_tokens").asLong(0);
                if (pt > 0 || ct > 0) com.example.youkeda_project.util.TokenStats.record("金融分析", pt, ct);
            }

            ChatResponse response = objectMapper.readValue(responseJson, ChatResponse.class);
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                return (String) response.getChoices().get(0).getMessage().content();
            }
            throw new RuntimeException("AI 未返回内容");
        } catch (Exception e) {
            throw new RuntimeException("意图解析调用失败: " + e.getMessage(), e);
        }
    }

    /** 从 AI 返回中提取 JSON。 */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        // 去掉 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        // 找到第一个 { 和最后一个 }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return "{}";
    }

    /** 解析意图 JSON 为 AnalysisIntent。 */
    @SuppressWarnings("unchecked")
    private AnalysisIntent parseIntentJson(String json, String originalQuery) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, LinkedHashMap.class);
            AnalysisIntent intent = new AnalysisIntent();

            String typeStr = (String) map.getOrDefault("type", "GENERAL");
            try {
                intent.setType(AnalysisType.valueOf(typeStr));
            } catch (IllegalArgumentException e) {
                intent.setType(AnalysisType.GENERAL);
            }

            intent.setStocks(safeList(map.get("stocks")));
            intent.setFutures(safeList(map.get("futures")));
            intent.setFunds(safeList(map.get("funds")));
            intent.setIndices(safeList(map.get("indices")));
            intent.setCurrencyPair((String) map.get("currencyPair"));
            intent.setNewsKeyword((String) map.get("newsKeyword"));
            intent.setTimeframe((String) map.getOrDefault("timeframe", "中期"));
            intent.setOriginalQuery(originalQuery);
            intent.setNeedsMoneyFlow(Boolean.TRUE.equals(map.get("needsMoneyFlow")));

            return intent;
        } catch (Exception e) {
            log.warn("解析意图JSON失败: {}", json, e);
            return fallbackIntent(originalQuery);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> safeList(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    /** 当意图解析失败时，使用通用分析模式。 */
    private AnalysisIntent fallbackIntent(String query) {
        return new AnalysisIntent()
                .setType(AnalysisType.GENERAL)
                .setNewsKeyword(query.length() > 20 ? query.substring(0, 20) : query)
                .setOriginalQuery(query);
    }

    // ==================== Prompt 构建 ====================

    /**
     * 根据分析类型构建对应的 System Prompt。
     */
    private String buildSystemPrompt(AnalysisType type) {
        String base = """
            你是一位资深金融分析师，拥有股票、期货、基金多领域投资研究经验。

            ## 分析准则
            1. **数据说话**：每个判断都要引用采集数据中的具体数字，不做空泛评论
            2. **多空两面**：既要说机会也要说风险，不偏颇
            3. **区分事实与观点**：「数据显示…」（事实）vs「综合来看…」（观点）
            4. **结论先行**：关键发现放在最前面，让读者30秒抓住重点
            5. **知识库融合**：若上下文包含【资金面数据】或知识库片段，需将方法论(如K线形态/量价关系/行业轮动规律/风控原则)融入分析，用数据验证方法论
            6. **无数据支撑的判断标「仅供参考」**
            7. **善用emoji**：📈📉⚠️✅🔥❄️💡 增强可读性

            ## 数据解读指引
            - 涨跌幅 > 2% 值得关注，> 5% 属于大幅波动
            - 成交量异常放大（> 近期均值2倍）提示情绪极端
            - 多品种同向波动 → 系统性因素；分化 → 结构性行情
            - 期货>股票>基金 的传导有时滞，通常1-3个交易日
            - 若含资金面数据：主力净流入 + 板块上涨 → 资金驱动可信；主力净流出 + 板块上涨 → 警惕回落

            ## 输出格式
            ### 一、关键数据速览
            （表格或列表，5-8个核心数字）
            ### 二、深度分析
            （至少2个分析角度，每段有数据支撑+结论）
            ### 三、综合研判
            （一句话定调 + 3条关键理由）
            ### 四、风险提示
            （2-3个具体风险，不是套话）

            ## 风险提示必须包含
            ⚠️ 免责声明：以上分析仅供参考，不构成投资建议。市场有风险，投资须谨慎。
            """;

        return switch (type) {
            case STOCK_ANALYSIS -> base + """
                ## 个股分析框架
                1. 当日走势：价格位置、涨跌幅、成交量是否异常
                2. 短期趋势：结合指数环境判断强弱
                3. 估值参考：如果知道PE/PB，给出估值分位判断
                4. 关注点：近期催化剂、业绩预期、机构动向
                """;
            case SECTOR_ANALYSIS -> base + """
                ## 产业链分析框架
                1. 产业链全景：上游原料→中游制造→下游终端，各环节今日表现
                2. 价格传导：原料价格变动对各环节利润的传导方向和时滞
                3. 资金面验证：结合【资金面数据】，验证板块行情是否得到主力资金认可
                   - 主力净流入 + 板块上涨 → 资金驱动，趋势可信度更高
                   - 主力净流出 + 板块上涨 → 散户推动，警惕冲高回落
                   - 主力净流入 + 板块下跌 → 资金低位吸筹，可能企稳
                4. 盈利弹性：原料价格每变动10%对主要公司毛利率的估算影响
                5. 产能周期：当前处于扩张/高峰/过剩/出清的哪个阶段
                6. 配置建议：看好/看空哪些环节的ETF和主题基金
                """;
            case CROSS_MARKET -> base + """
                ## 跨市场联动框架
                1. 核心驱动因子：识别是什么在推动市场联动
                2. 期货→股票传导：成本端和收入端分别分析
                3. 股票→基金传导：哪些基金重仓受影响标的
                4. 汇率/利率冲击：外部变量对各市场的影响力度
                5. 套利/对冲思路：是否存在跨品种的对冲机会「仅供参考」
                """;
            case FUND_ANALYSIS -> base + """
                ## 基金分析框架
                1. 净值表现：近期涨跌、同类排名（如有数据）
                2. 风险收益：波动率、最大回撤的判断
                3. 持仓透视：从前十大重仓推断投资风格
                4. 费效比：费率水平是否合理
                5. 对比：与同类基金和对应ETF的横向比较
                6. 适合人群：该基金适合什么类型的投资者
                """;
            case PORTFOLIO_DIAGNOSIS -> base + """
                ## 持仓诊断框架
                1. 相关性分析：持仓品种之间的联动程度
                2. 集中度风险：行业/主题集中度是否过高
                3. 风格暴露：大盘/小盘、价值/成长、周期/防御
                4. 压力测试：极端行情下组合可能的最大回撤
                5. 优化建议：减配/增配/替代方案
                """;
            case FUTURES_TREND -> base + """
                ## 期货趋势分析框架
                1. 价格趋势：当前处于上涨/下跌/震荡的哪个阶段
                2. 基差结构：升贴水形态及其市场含义
                3. 供需分析：库存水平、产能利用率、季节性规律
                4. 资金面：持仓量变化反映的多空博弈
                5. 关键价位：技术上的支撑位和压力位
                """;
            case MARKET_OVERVIEW -> base + """
                ## 大盘综述框架
                1. 指数全景：主要指数涨跌和成交量一览
                2. 市场情绪：从涨跌比、量能判断情绪温度
                3. 风格轮动：大盘/小盘、价值/成长谁在领跑
                4. 资金信号：结合【资金面数据】分析
                   - 北向资金动向（持续流入/流出）、主力板块偏好
                   - 成交量变化透露的资金态度
                   - 资金面+技术面交叉验证
                5. 外部环境：汇率、商品、海外市场的共振
                6. 短期研判：未来1-2周可能的方向
                """;
            default -> base;
        };
    }

    /**
     * 构建发送给 AI 的分析请求。
     */
    private String buildAnalysisRequest(String query, AnalysisIntent intent, String dataContext) {
        return String.format("""
                【用户问题】
                %s

                【分析类型】%s
                【时间维度】%s

                【实时采集数据】
                %s

                请基于以上实时数据，按指定框架进行全面分析。
                注意：如果某项数据缺失或异常，明确指出并基于已有数据给出判断。""",
                query,
                intent.getType().getLabel(),
                intent.getTimeframe(),
                dataContext);
    }

    // ==================== 免责声明兜底 ====================

    /**
     * 确保分析结果中包含免责声明。
     * 如果 AI 生成的报告已包含免责声明则不做修改，否则追加。
     */
    private String ensureDisclaimer(String result) {
        if (result == null || result.isBlank()) {
            return "抱歉，分析未能生成有效结果。" + DISCLAIMER;
        }
        // 已包含免责声明关键词则跳过
        if (result.contains(DISCLAIMER_SHORT) || result.contains("投资须谨慎") || result.contains("不构成投资建议")) {
            return result;
        }
        // 追加免责声明
        return result + DISCLAIMER;
    }
}
