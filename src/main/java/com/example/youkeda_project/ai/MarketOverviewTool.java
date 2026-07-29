package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ExchangeRateService;
import com.example.youkeda_project.service.NewsService;
import com.example.youkeda_project.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MarketOverviewTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MarketOverviewTool.class);

    private static final List<String> INDEXES = List.of(
        "上证指数", "深证成指", "创业板指", "科创50", "沪深300"
    );

    private static final List<String> HOT_SECTORS = List.of(
        "贵州茅台", "工商银行", "中信证券", "宁德时代", "中芯国际",
        "中国平安", "中国石油", "科大讯飞", "比亚迪"
    );

    private final StockService stockService;
    private final NewsService newsService;
    private final ExchangeRateService exchangeRateService;

    public MarketOverviewTool(StockService stockService, NewsService newsService,
                              ExchangeRateService exchangeRateService) {
        this.stockService = stockService;
        this.newsService = newsService;
        this.exchangeRateService = exchangeRateService;
    }

    @Override
    public String getName() {
        return "market_overview";
    }

    @Override
    public String getDescription() {
        return "获取A股大盘概览，包括主要指数行情、热点板块、汇率、最新财经新闻。"
            + "用户问今天大盘怎么样、股市行情、市场概况时调用。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        params.put("required", List.of());
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder("📊 A股大盘概览\n\n");

        // 指数行情
        sb.append("**主要指数**\n");
        for (String name : INDEXES) {
            try {
                String quote = stockService.queryStock(name);
                String[] lines = quote.split("\n");
                for (String line : lines) {
                    if (line.contains("当前价") || line.contains("涨跌") || line.contains("涨跌幅")) {
                        sb.append(name).append("：").append(line.trim()).append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("查询指数失败: {}", name);
            }
        }

        // 热点板块（查询各板块代表股）
        sb.append("\n**热点板块**\n");
        for (String stock : HOT_SECTORS) {
            try {
                String quote = stockService.queryStock(stock);
                String[] lines = quote.split("\n");
                String price = "", change = "";
                for (String line : lines) {
                    if (line.contains("当前价")) price = line.trim();
                    if (line.contains("涨跌幅")) change = line.trim();
                }
                sb.append(stock).append("：").append(price).append(" ").append(change).append("\n");
            } catch (Exception e) {
                log.warn("查询板块股失败: {}", stock);
            }
        }

        // 汇率
        sb.append("\n**汇率**\n");
        try {
            String rate = exchangeRateService.convert("USD", "CNY", 1);
            if (rate != null && !rate.startsWith("错误")) {
                sb.append("美元/人民币：").append(rate).append("\n");
            }
        } catch (Exception e) {
            sb.append("美元/人民币：（获取失败）\n");
        }
        try {
            String rate = exchangeRateService.convert("EUR", "CNY", 1);
            if (rate != null && !rate.startsWith("错误")) {
                sb.append("欧元/人民币：").append(rate).append("\n");
            }
        } catch (Exception e) {
            sb.append("欧元/人民币：（获取失败）\n");
        }
        try {
            String rate = exchangeRateService.convert("HKD", "CNY", 1);
            if (rate != null && !rate.startsWith("错误")) {
                sb.append("港币/人民币：").append(rate).append("\n");
            }
        } catch (Exception e) {
            sb.append("港币/人民币：（获取失败）\n");
        }

        // 财经新闻
        sb.append("\n**最新财经资讯**\n");
        try {
            String news = newsService.searchNews(8, "财经 股市");
            if (news != null && !news.startsWith("错误:")) {
                sb.append(news);
            } else {
                sb.append("（暂无财经新闻）\n");
            }
        } catch (Exception e) {
            log.warn("获取新闻失败: {}", e.getMessage());
            sb.append("（新闻获取失败）\n");
        }

        return ToolResult.text(sb.toString());
    }
}
