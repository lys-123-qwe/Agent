package com.example.youkeda_project.service.stock;

import com.example.youkeda_project.ai.agent.AnalysisIntent;
import com.example.youkeda_project.ai.agent.FinDataBundle;
import com.example.youkeda_project.service.stock.ExchangeRateService;
import com.example.youkeda_project.service.stock.FundService;
import com.example.youkeda_project.service.stock.FuturesService;
import com.example.youkeda_project.service.stock.NewsService;
import com.example.youkeda_project.service.stock.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 金融数据聚合服务。
 * <p>
 * 根据 AnalysisIntent 并行从多个数据源（股票/期货/基金/汇率/新闻/指数）
 * 采集实时行情数据，聚合为 FinDataBundle。
 * <p>
 * 使用虚拟线程并发采集，单个接口超时 5 秒不阻塞整体。
 */

@Service
public class FinDataService {

    private static final Logger log = LoggerFactory.getLogger(FinDataService.class);

    private static final int TIMEOUT_SECONDS = 5;

    private final StockService stockService;
    private final FuturesService futuresService;
    private final FundService fundService;
    private final ExchangeRateService exchangeRateService;
    private final NewsService newsService;
    private final MoneyFlowService moneyFlowService;
    private final ExecutorService executor;

    public FinDataService(StockService stockService,
                          FuturesService futuresService,
                          FundService fundService,
                          ExchangeRateService exchangeRateService,
                          NewsService newsService,
                          MoneyFlowService moneyFlowService) {
        this.stockService = stockService;
        this.futuresService = futuresService;
        this.fundService = fundService;
        this.exchangeRateService = exchangeRateService;
        this.newsService = newsService;
        this.moneyFlowService = moneyFlowService;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 根据分析意图并行采集所有需要的数据。
     *
     * @param intent 分析意图（含需要的品种列表）
     * @return 聚合数据容器
     */
    public FinDataBundle collect(AnalysisIntent intent) {
        FinDataBundle bundle = new FinDataBundle();
        long t0 = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        // 合并所有目标品种（用于跳过默认指数中已在目标列表中的项）
        java.util.Set<String> allTargets = new java.util.HashSet<>();
        allTargets.addAll(intent.getStocks());
        allTargets.addAll(intent.getIndices());
        allTargets.addAll(intent.getFutures());
        allTargets.addAll(intent.getFunds());

        // 大盘指数（始终采集，但跳过已在目标列表中的避免重复）
        futures.add(CompletableFuture.runAsync(() -> collectIndices(bundle, allTargets), executor));

        // 期货数据
        if (intent.hasFutures()) {
            for (String f : intent.getFutures()) {
                futures.add(CompletableFuture.runAsync(() -> collectOneFutures(bundle, f), executor));
            }
        }

        // 股票数据
        if (intent.hasStocks()) {
            for (String s : intent.getStocks()) {
                futures.add(CompletableFuture.runAsync(() -> collectOneStock(bundle, s), executor));
            }
        }
        // 指数（可能单独指定）
        if (intent.hasIndices()) {
            for (String idx : intent.getIndices()) {
                futures.add(CompletableFuture.runAsync(() -> collectOneStock(bundle, idx), executor));
            }
        }

        // 基金数据
        if (intent.hasFunds()) {
            for (String f : intent.getFunds()) {
                futures.add(CompletableFuture.runAsync(() -> collectOneFund(bundle, f), executor));
            }
        }

        // 汇率数据
        if (intent.needsExchangeRate()) {
            futures.add(CompletableFuture.runAsync(() -> collectExchangeRate(bundle, intent.getCurrencyPair()), executor));
        }

        // 新闻资讯
        if (intent.needsNews()) {
            futures.add(CompletableFuture.runAsync(() -> collectNews(bundle, intent.getNewsKeyword()), executor));
        }

        // 资金流向数据（板块分析、大盘综述、跨市场联动等场景自动采集）
        if (intent.needsMoneyFlow() || shouldAutoCollectMoneyFlow(intent.getType())) {
            futures.add(CompletableFuture.runAsync(() -> collectMoneyFlow(bundle), executor));
        }

        // 如果没有任何指定品种，至少采集大盘指数和新闻
        if (intent.isEmpty()) {
            futures.add(CompletableFuture.runAsync(() -> collectNewsForOverview(bundle), executor));
        }

        // 等待所有任务完成（带超时）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("数据采集超时或中断: {}", e.getMessage());
            bundle.addError("部分数据采集超时，以下分析基于可用数据");
        }

        long elapsed = System.currentTimeMillis() - t0;
        log.info("数据采集完成: {}ms, stocks={}, futures={}, funds={}, errors={}",
                elapsed, bundle.getStockResults().size(), bundle.getFuturesResults().size(),
                bundle.getFundResults().size(), bundle.getErrors().size());

        return bundle;
    }

    // ==================== 各项采集方法 ====================

    private void collectIndices(FinDataBundle bundle, java.util.Set<String> skipSet) {
        String[] defaultIndices = {"上证指数", "深证成指", "创业板指", "科创50", "沪深300"};
        for (String idx : defaultIndices) {
            if (skipSet.contains(idx)) continue; // 避免与 stock 采集重复
            try {
                String result = stockService.queryStock(idx);
                if (result != null && !result.startsWith("错误:")) {
                    bundle.addIndexResult(idx, result);
                }
            } catch (Exception e) {
                log.debug("采集指数 {} 失败: {}", idx, e.getMessage());
            }
        }
    }

    private void collectOneFutures(FinDataBundle bundle, String name) {
        try {
            String result = futuresService.queryFutures(name);
            if (result != null && !result.startsWith("错误:")) {
                bundle.addFuturesResult(name, result);
            } else {
                bundle.addError("期货「" + name + "」: " + (result != null ? result : "无数据"));
            }
        } catch (Exception e) {
            log.warn("采集期货 {} 失败: {}", name, e.getMessage());
            bundle.addError("期货「" + name + "」采集失败");
        }
    }

    private void collectOneStock(FinDataBundle bundle, String name) {
        try {
            String result = stockService.queryStock(name);
            if (result != null && !result.startsWith("错误:")) {
                bundle.addStockResult(name, result);
            } else {
                bundle.addError("股票「" + name + "」: " + (result != null ? result : "无数据"));
            }
        } catch (Exception e) {
            log.warn("采集股票 {} 失败: {}", name, e.getMessage());
            bundle.addError("股票「" + name + "」采集失败");
        }
    }

    private void collectOneFund(FinDataBundle bundle, String name) {
        try {
            String result = fundService.queryFund(name);
            if (result != null && !result.startsWith("错误:")) {
                bundle.addFundResult(name, result);
            } else {
                bundle.addError("基金「" + name + "」: " + (result != null ? result : "无数据"));
            }
        } catch (Exception e) {
            log.warn("采集基金 {} 失败: {}", name, e.getMessage());
            bundle.addError("基金「" + name + "」采集失败");
        }
    }

    private void collectExchangeRate(FinDataBundle bundle, String currencyPair) {
        try {
            String[] parts = currencyPair.split("/");
            String from = parts.length > 0 ? parts[0].trim().toUpperCase() : "USD";
            String to = parts.length > 1 ? parts[1].trim().toUpperCase() : "CNY";
            String result = exchangeRateService.convert(from, to, 1.0);
            bundle.setExchangeRateResult(result);
        } catch (Exception e) {
            log.warn("采集汇率失败: {}", e.getMessage());
        }
    }

    private void collectNews(FinDataBundle bundle, String keyword) {
        try {
            String result = newsService.searchNews(5, keyword);
            if (result != null && !result.isBlank()) {
                bundle.setNewsResult(result);
            }
        } catch (Exception e) {
            log.warn("采集新闻失败: {}", e.getMessage());
        }
    }

    private void collectNewsForOverview(FinDataBundle bundle) {
        try {
            String result = newsService.searchNews(5, "");
            if (result != null && !result.isBlank()) {
                bundle.setNewsResult(result);
            }
        } catch (Exception e) {
            log.debug("采集综合新闻失败: {}", e.getMessage());
        }
    }

    /**
     * 采集资金流向数据。
     */
    private void collectMoneyFlow(FinDataBundle bundle) {
        try {
            String result = moneyFlowService.getMoneyFlowSummary();
            if (result != null && !result.isBlank()) {
                bundle.setMoneyFlowResult(result);
            }
        } catch (Exception e) {
            log.debug("采集资金流向失败: {}", e.getMessage());
        }
    }

    /**
     * 判断是否应自动采集资金流向数据。
     * 板块分析、大盘综述、跨市场联动、持仓诊断等场景默认需要资金面数据。
     */
    private boolean shouldAutoCollectMoneyFlow(com.example.youkeda_project.ai.agent.AnalysisType type) {
        return switch (type) {
            case SECTOR_ANALYSIS, MARKET_OVERVIEW, CROSS_MARKET,
                 PORTFOLIO_DIAGNOSIS, NEWS_IMPACT, ASSET_ALLOCATION -> true;
            default -> false;
        };
    }
}
