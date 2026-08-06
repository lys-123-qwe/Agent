package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.tool.ToolResult;

import com.example.youkeda_project.ai.agent.FinAnalysisAgent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 金融综合分析工具（Function Calling）。
 * <p>
 * 定义 fin_analysis 函数，供 DeepSeek 通过 Function Calling 调用。
 * 委托 FinAnalysisAgent 执行完整的金融分析流程：
 * 意图解析 → 多源数据采集 → AI 推理 → 分析报告。
 * <p>
 * 适用场景：个股/基金深度分析、板块产业链分析、跨市场联动、
 * 持仓诊断、资产配置、大盘综评等需要综合多维金融数据的分析任务。
 */

@Component
public class FinAnalysisTool extends AbstractTool {

        private final FinAnalysisAgent finAnalysisAgent;

    public FinAnalysisTool(FinAnalysisAgent finAnalysisAgent) {
        this.finAnalysisAgent = finAnalysisAgent;
    }

    @Override
    public String getName() {
        return "fin_analysis";
    }

    @Override
    public String getDescription() {
        return "金融综合分析工具，可对股票、期货、基金、板块进行多维度的深度分析。"
            + "适用场景："
            + "① 个股/基金深度分析（分析某只股票或基金的走势、估值、风险）；"
            + "② 产业链分析（分析某个行业板块的上下游联动关系）；"
            + "③ 跨市场分析（期货价格变动对股票和基金的影响）；"
            + "④ 持仓诊断（分析用户持仓组合的风险和优化方向）；"
            + "⑤ 大盘综述（当日市场全景分析）；"
            + "⑥ 资讯解读（分析新闻对市场的影响）。"
            + "当用户的问题涉及多个金融品种、需要综合分析、或需要跨市场对比时，优先使用此工具。"
            + "注意：单个品种的简单查询（如查股价/净值/期货价格）请使用对应的 get_stock/get_fund/get_futures 工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "用户的完整分析需求，包含所有关键信息。例如："
            + "'分析新能源汽车产业链的投资机会'、"
            + "'原油大跌对A股航空和化工板块的影响'、"
            + "'我持有宁德时代+新能源ETF，帮我看看持仓风险'、"
            + "'今天A股大盘怎么样'。");
        properties.put("query", query);

        Map<String, Object> analysisType = new LinkedHashMap<>();
        analysisType.put("type", "string");
        analysisType.put("description", "分析类型。可选值：auto(自动判断)、stock(个股分析)、"
            + "fund(基金分析)、futures(期货趋势)、sector(板块产业链)、"
            + "cross_market(跨市场联动)、portfolio(持仓诊断)、"
            + "allocation(资产配置)、market_overview(大盘综述)、news_impact(资讯解读)。"
            + "不确定时填 auto。");
        properties.put("analysis_type", analysisType);

        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("type", "array");
        targets.put("items", Map.of("type", "string"));
        targets.put("description", "涉及的金融品种名称列表（股票名/代码、期货品种、基金名称），"
            + "如 ['比亚迪', '宁德时代', '碳酸锂', '新能源ETF']。如果用户没有明确指定，填 []。");
        properties.put("targets", targets);

        params.put("properties", properties);
        params.put("required", java.util.List.of("query"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String query = arguments != null ? (String) arguments.get("query") : null;
        if (query == null || query.isBlank()) {
            return ToolResult.text("错误: 请提供分析需求");
        }

        String userId = AIService.getCurrentUserId();
        if (userId == null) {
            userId = "CLI";
        }

        log.info("FinAnalysisTool 执行: userId={}, query={}", userId,
                query.length() > 80 ? query.substring(0, 80) + "..." : query);

        // 通知用户分析正在进行（可能需要几秒）
        String result = finAnalysisAgent.analyze(userId, query);
        return ToolResult.text(result);
    }
}
