package com.example.youkeda_project.skill.financial;

import com.example.youkeda_project.ai.FinancialAnalysisTool;
import com.example.youkeda_project.skill.AbstractSkill;
import com.example.youkeda_project.ai.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 金融分析 Skill.
 *
 * 提供基于技术指标(MA/RSI/MACD/布林带)的股票走势预测工具.
 */
@Component
public class FinancialAnalysisSkill extends AbstractSkill {

    public FinancialAnalysisSkill(FinancialAnalysisTool analysisTool) {
        super(
            "financial-analysis",
            "金融分析智能体:基于 MA/RSI/MACD/布林带 等技术指标预测股票/指数走势,提供方向判断,置信度,预测有效期和底部/顶部区域估算.",
            List.of(
                "分析一下股票走势",
                "贵州茅台未来怎么看",
                "特斯拉会涨吗",
                "上证指数什么时候触底",
                "比特币趋势分析",
                "BTC 趋势分析",
                "这只股票什么时候见顶",
                "怎么看美股",
                "港股走势预测",
                "技术面分析"
            ),
            List.of(analysisTool),
            List.of(
                "knowledge/technical-analysis.md",
                "knowledge/lean-patterns.md",
                "knowledge/candlestick-patterns.md",
                "knowledge/volume-analysis.md",
                "knowledge/sector-rotation.md",
                "knowledge/risk-management.md",
                "knowledge/macro-indicators.md",
                "knowledge/fund-selection.md"
            )
        );
    }
}
