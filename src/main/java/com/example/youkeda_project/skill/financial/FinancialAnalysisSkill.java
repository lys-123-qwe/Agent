package com.example.youkeda_project.skill.financial;

import com.example.youkeda_project.ai.tool.AddPositionTool;
import com.example.youkeda_project.ai.tool.AddToWatchlistTool;
import com.example.youkeda_project.ai.tool.ComparisonTool;
import com.example.youkeda_project.ai.tool.ExchangeRateTool;
import com.example.youkeda_project.ai.tool.FinancialAnalysisTool;
import com.example.youkeda_project.ai.tool.FundTool;
import com.example.youkeda_project.ai.tool.FuturesTool;
import com.example.youkeda_project.ai.tool.ListPositionsTool;
import com.example.youkeda_project.ai.tool.ListWatchlistTool;
import com.example.youkeda_project.ai.tool.MarketOverviewTool;
import com.example.youkeda_project.ai.tool.RemoveFromWatchlistTool;
import com.example.youkeda_project.ai.tool.RemovePositionTool;
import com.example.youkeda_project.ai.tool.StockTool;
import com.example.youkeda_project.ai.tool.TechnicalTool;
import com.example.youkeda_project.skill.AbstractSkill;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FinancialAnalysisSkill extends AbstractSkill {

    public FinancialAnalysisSkill(
            FinancialAnalysisTool analysisTool,
            StockTool stockTool,
            ComparisonTool comparisonTool,
            TechnicalTool technicalTool,
            MarketOverviewTool marketOverviewTool,
            FuturesTool futuresTool,
            FundTool fundTool,
            ExchangeRateTool exchangeRateTool,
            AddToWatchlistTool addToWatchlistTool,
            RemoveFromWatchlistTool removeFromWatchlistTool,
            ListWatchlistTool listWatchlistTool,
            AddPositionTool addPositionTool,
            RemovePositionTool removePositionTool,
            ListPositionsTool listPositionsTool) {
        super(
            "financial-analysis",
            "金融分析:提供股票/基金/期货/外汇的实时行情查询,技术面分析(MA/RSI/MACD/布林带),多标的对比,走势预测,大盘概况.涵盖A股,港股,美股,国内国际期货,开放式基金和ETF.",
            List.of(
                "分析一下股票走势",
                "贵州茅台未来怎么看",
                "特斯拉会涨吗",
                "上证指数什么时候触底",
                "对比一下茅台和五粮液",
                "大盘今天怎么样",
                "查一下黄金期货",
                "沪深300ETF怎么样",
                "美元兑人民币汇率",
                "技术面分析",
                "这只股票什么时候见顶",
                "怎么看美股",
                "港股走势预测"
            ),
            List.of(analysisTool, stockTool, comparisonTool, technicalTool,
                     marketOverviewTool, futuresTool, fundTool, exchangeRateTool,
                     addToWatchlistTool, removeFromWatchlistTool, listWatchlistTool,
                     addPositionTool, removePositionTool, listPositionsTool),
            List.of("knowledge/technical-analysis.md", "knowledge/lean-patterns.md")
        );
    }
}
