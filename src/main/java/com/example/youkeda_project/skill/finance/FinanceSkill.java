package com.example.youkeda_project.skill.finance;

import com.example.youkeda_project.skill.AbstractSkill;
import com.example.youkeda_project.tool.finance.ChartTool;
import com.example.youkeda_project.tool.finance.CompareFundsTool;
import com.example.youkeda_project.tool.finance.ComparisonTool;
import com.example.youkeda_project.tool.finance.ExchangeRateTool;
import com.example.youkeda_project.tool.finance.FinAnalysisTool;
import com.example.youkeda_project.tool.finance.FinancialAnalysisTool;
import com.example.youkeda_project.tool.finance.FundTool;
import com.example.youkeda_project.tool.finance.FuturesTool;
import com.example.youkeda_project.tool.finance.MarketOverviewTool;
import com.example.youkeda_project.tool.finance.MoneyFlowTool;
import com.example.youkeda_project.tool.finance.PositionTool;
import com.example.youkeda_project.tool.finance.StockTool;
import com.example.youkeda_project.tool.finance.TechnicalTool;
import com.example.youkeda_project.tool.finance.WatchlistTool;
import com.example.youkeda_project.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 金融分析技能：封装全部金融工具（行情/分析/对比/图表/持仓/自选/资金流向等）。
 */
@Component
public class FinanceSkill extends AbstractSkill {

    public FinanceSkill(
            StockTool stockTool,
            TechnicalTool technicalTool,
            FinancialAnalysisTool financialAnalysisTool,
            ComparisonTool comparisonTool,
            MarketOverviewTool marketOverviewTool,
            ChartTool chartTool,
            FuturesTool futuresTool,
            FundTool fundTool,
            ExchangeRateTool exchangeRateTool,
            MoneyFlowTool moneyFlowTool,
            FinAnalysisTool finAnalysisTool,
            CompareFundsTool compareFundsTool,
            PositionTool positionTool,
            WatchlistTool watchlistTool) {
        super(
            "financial-analysis",
            "金融分析:提供股票/基金/期货/外汇的实时行情查询,技术面分析(MA/RSI/MACD/布林带),多标的对比,走势预测,大盘概况,K线图,资金流向,持仓与自选管理.涵盖A股,港股,美股,国内国际期货,开放式基金和ETF.",
            List.of(
                "分析一下股票走势",
                "贵州茅台未来怎么看",
                "特斯拉会涨吗",
                "上证指数什么时候触底",
                "比特币趋势分析",
                "这只股票什么时候见顶",
                "怎么看美股",
                "港股走势预测",
                "技术面分析",
                "主力资金在买什么",
                "板块轮动怎么看",
                "如何控制仓位",
                "美联储加息有什么影响",
                "基金怎么选",
                "现在该买什么基金",
                "对比一下茅台和五粮液",
                "大盘今天怎么样",
                "查一下黄金期货",
                "沪深300ETF怎么样",
                "美元兑人民币汇率",
                "画个K线图"
            ),
            List.of(stockTool, technicalTool, financialAnalysisTool, comparisonTool,
                    marketOverviewTool, chartTool, futuresTool, fundTool,
                    exchangeRateTool, moneyFlowTool, finAnalysisTool, compareFundsTool,
                    positionTool, watchlistTool),
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
