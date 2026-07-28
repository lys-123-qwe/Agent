package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ExchangeRateService;
import com.example.youkeda_project.service.FundService;
import com.example.youkeda_project.service.FuturesService;
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

    private final StockService stockService;
    private final FuturesService futuresService;
    private final ExchangeRateService exchangeRateService;
    private final FundService fundService;
    private final NewsService newsService;

    private static final List<String> INDICES = List.of(
        "上证指数", "深证成指", "创业板指", "科创50", "沪深300");

    private static final List<String> FUTURES = List.of(
        "沪金", "原油", "螺纹钢", "沪铜", "铁矿石");

    private static final List<String> FUNDS = List.of(
        "招商中证白酒", "易方达蓝筹精选", "中概互联");

    public MarketOverviewTool(StockService stockService, FuturesService futuresService,
                              ExchangeRateService exchangeRateService, FundService fundService,
                              NewsService newsService) {
        this.stockService = stockService;
        this.futuresService = futuresService;
        this.exchangeRateService = exchangeRateService;
        this.fundService = fundService;
        this.newsService = newsService;
    }

    @Override
    public String getName() {
        return "market_overview";
    }

    @Override
    public String getDescription() {
        return "查询今日全市场总览，包括A股主要指数、期货行情、汇率、热门板块、基金昨日净值、财经要闻。不传参数直接获取。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder();
        sb.append("【今日全市场总览】\n\n");

        sb.append("📈 A股指数:\n");
        for (String name : INDICES) {
            String r = stockService.queryStock(name);
            if (r != null && !r.startsWith("错误:")) {
                sb.append(r).append("\n");
            } else {
                sb.append(name).append(": 暂未获取到数据\n");
            }
        }

        sb.append("\n📊 期货:\n");
        for (String name : FUTURES) {
            String r = futuresService.query(name);
            if (r != null && !r.startsWith("不支持的") && !r.startsWith("请提供")) {
                sb.append(r).append("\n");
            } else {
                sb.append(name).append(": 暂未获取到数据\n");
            }
        }

        sb.append("\n💱 汇率:\n");
        addRate(sb, "美元/人民币", "USD", "CNY");
        addRate(sb, "欧元/人民币", "EUR", "CNY");
        addRate(sb, "港币/人民币", "HKD", "CNY");
        addRate(sb, "日元/人民币", "JPY", "CNY");
        addRate(sb, "英镑/人民币", "GBP", "CNY");

        sb.append("\n📈 基金 (昨日净值):\n");
        for (String name : FUNDS) {
            String r = fundService.query(name);
            if (r != null && !r.startsWith("不支持的")) {
                sb.append(r).append("\n");
            } else {
                sb.append(name).append(": 暂未获取到数据\n");
            }
        }

        sb.append("\n🔥 热门板块:\n");
        String sectorResult = newsService.searchNews(5, "今日板块资金流向排行", "财经");
        sb.append(sectorResult != null ? sectorResult : "暂未获取到板块数据\n");

        sb.append("\n📰 财经要闻:\n");
        String newsResult = newsService.searchNews(5, "今日财经要闻", "财经");
        sb.append(newsResult != null ? newsResult : "暂未获取到新闻\n");

        return ToolResult.text(sb.toString());
    }

    private void addRate(StringBuilder sb, String label, String from, String to) {
        String r = exchangeRateService.convert(from, to, 1);
        if (r != null && !r.startsWith("错误:")) {
            String rate = r.replaceAll(".*conversion_rate[^0-9]*([\\d.]+).*", "$1");
            if (!rate.matches("^[\\d.]+$")) {
                rate = r.replaceAll(".*= ([\\d.]+) CNY.*", "$1");
            }
            sb.append("  ").append(label).append(": ").append(rate).append("\n");
        } else {
            sb.append("  ").append(label).append(": --\n");
        }
    }
}
