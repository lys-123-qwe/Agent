package com.example.youkeda_project.ai;

import com.example.youkeda_project.ai.agent.FinancialAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FinancialAnalysisTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FinancialAnalysisTool.class);

    private final FinancialAgent financialAgent;

    public FinancialAnalysisTool(FinancialAgent financialAgent) {
        this.financialAgent = financialAgent;
    }

    @Override
    public String getName() {
        return "financial_analysis";
    }

    @Override
    public String getDescription() {
        return """
            【金融分析智能体】对股票、基金、期货进行专业分析。
            传入分析问题或标的代码，返回分析报告和投资建议。
            触发关键词：分析、诊断、建议、走势、行情分析、投资分析、推荐""";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
            "type", "string",
            "description", "分析问题或标的代码，如：分析沪深300ETF、sh000300、现在适合买什么基金"
        ));
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("query"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (args == null || !args.containsKey("query")
                || args.get("query") == null) {
            return ToolResult.text("错误：请提供分析问题或标的代码");
        }

        String query = String.valueOf(args.get("query"));
        String userId = AIService.getCurrentUserId();

        log.info("FinancialAnalysisTool 执行: query={}, user={}", query, userId);

        String result = financialAgent.analyze(userId, query);
        return ToolResult.text(result);
    }
}