package com.example.youkeda_project.ai.agent;

import com.example.youkeda_project.ai.ChatResult;
import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.service.FundService;
import com.example.youkeda_project.service.FuturesService;
import com.example.youkeda_project.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class FinancialAgent {

    private static final Logger log = LoggerFactory.getLogger(FinancialAgent.class);

    private final AIService aiService;
    private final StockService stockService;
    private final FuturesService futuresService;
    private final FundService fundService;
    private final ExecutorService agentExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "financial-agent");
        t.setDaemon(true);
        return t;
    });

    public FinancialAgent(AIService aiService, StockService stockService,
                           FuturesService futuresService, FundService fundService) {
        this.aiService = aiService;
        this.stockService = stockService;
        this.futuresService = futuresService;
        this.fundService = fundService;
    }

    /**
     * 智能体主入口：并行获取数据 + AI 生成分析报告。
     */
    public String analyze(String userId, String userMsg) {
        log.info("FinancialAgent 分析请求: user={}, query={}", userId, userMsg);

        String today = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"));

        // 步骤1：并行获取数据
        CompletableFuture<String> stockFuture = CompletableFuture.supplyAsync(
            () -> safeQuery(() -> stockService.queryStock(userMsg)), agentExecutor);
        CompletableFuture<String> futuresFuture = CompletableFuture.supplyAsync(
            () -> safeQuery(() -> futuresService.queryFutures(userMsg)), agentExecutor);
        CompletableFuture<String> fundFuture = CompletableFuture.supplyAsync(
            () -> safeQuery(() -> fundService.queryFund(userMsg)), agentExecutor);
        CompletableFuture.allOf(stockFuture, futuresFuture, fundFuture).join();

        String stockData = stockFuture.join();
        String futuresData = futuresFuture.join();
        String fundData = fundFuture.join();

        // 步骤2：组装上下文
        String context = buildAnalysisContext(userMsg, stockData, futuresData, fundData);

        // 步骤3：调用 AI 生成分析报告（纯文本，无工具调用）
        return callAnalysisAI(userId, today, context);
    }

    private String safeQuery(QueryCallable callable) {
        try {
            String result = callable.query();
            return result != null ? result : "";
        } catch (Exception e) {
            log.warn("FinancialAgent 数据获取异常: {}", e.getMessage());
            return "";
        }
    }

    private String buildAnalysisContext(String query, String stockData,
                                         String futuresData, String fundData) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户查询: ").append(query).append("\n");
        sb.append("当前日期: ").append(LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"))).append("\n\n");

        boolean hasStock = !stockData.isEmpty() && !isNoData(stockData);
        boolean hasFutures = !futuresData.isEmpty() && !isNoData(futuresData);
        boolean hasFund = !fundData.isEmpty() && !isNoData(fundData);

        if (hasStock) {
            sb.append("=== 股票/行情数据 ===\n").append(stockData).append("\n\n");
        }
        if (hasFutures) {
            sb.append("=== 期货行情数据 ===\n").append(futuresData).append("\n\n");
        }
        if (hasFund) {
            sb.append("=== 基金净值数据 ===\n").append(fundData).append("\n\n");
        }

        if (!hasStock && !hasFutures && !hasFund) {
            sb.append("未找到相关标的的数据，请确认代码或名称是否正确。\n");
        }

        return sb.toString();
    }

    private boolean isNoData(String data) {
        return data.startsWith("未找到") || data.startsWith("错误")
            || data.startsWith("暂无") || data.startsWith("查询失败");
    }

    /**
     * 调用 DeepSeek 进行文本分析（不传 tools，纯文本对话）。
     */
    private String callAnalysisAI(String userId, String today, String context) {
        String systemPrompt = "今天是" + today + "。\n\n" + getSystemPrompt();

        String userMsg = systemPrompt + "\n\n=== 数据上下文 ===\n" + context
            + "\n\n请基于以上数据，对用户的问题进行专业分析，给出投资建议。";

        ChatResult result = aiService.chat(userId, userMsg, null);
        return result.getReplyText();
    }

    public String getSystemPrompt() {
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

    @FunctionalInterface
    private interface QueryCallable {
        String query() throws Exception;
    }
}