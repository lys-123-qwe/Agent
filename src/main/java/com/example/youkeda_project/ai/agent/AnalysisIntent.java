package com.example.youkeda_project.ai.agent;

import java.util.Collections;
import java.util.List;

/**
 * 用户金融分析意图解析结果。
 * <p>
 * 由 FinAnalysisAgent 通过 AI 解析用户问题生成，
 * 指导 FinDataService 采集哪些数据。
 */

public class AnalysisIntent {

    private AnalysisType type;
    private List<String> stocks;
    private List<String> futures;
    private List<String> funds;
    private List<String> indices;
    private String currencyPair;
    private String newsKeyword;
    private String timeframe;
    private String originalQuery;
    private boolean needsMoneyFlow;

    public AnalysisIntent() {
        this.type = AnalysisType.GENERAL;
        this.stocks = List.of();
        this.futures = List.of();
        this.funds = List.of();
        this.indices = List.of();
        this.currencyPair = null;
        this.newsKeyword = null;
        this.timeframe = "中期";
        this.originalQuery = "";
    }

    // ==================== Getters ====================

    public AnalysisType getType() { return type; }
    public List<String> getStocks() { return stocks != null ? stocks : List.of(); }
    public List<String> getFutures() { return futures != null ? futures : List.of(); }
    public List<String> getFunds() { return funds != null ? funds : List.of(); }
    public List<String> getIndices() { return indices != null ? indices : List.of(); }
    public String getCurrencyPair() { return currencyPair; }
    public String getNewsKeyword() { return newsKeyword; }
    public String getTimeframe() { return timeframe != null ? timeframe : "中期"; }
    public String getOriginalQuery() { return originalQuery; }
    public boolean needsMoneyFlow() { return needsMoneyFlow; }

    // ==================== Setters (fluent) ====================

    public AnalysisIntent setType(AnalysisType type) { this.type = type; return this; }
    public AnalysisIntent setStocks(List<String> stocks) { this.stocks = stocks; return this; }
    public AnalysisIntent setFutures(List<String> futures) { this.futures = futures; return this; }
    public AnalysisIntent setFunds(List<String> funds) { this.funds = funds; return this; }
    public AnalysisIntent setIndices(List<String> indices) { this.indices = indices; return this; }
    public AnalysisIntent setCurrencyPair(String currencyPair) { this.currencyPair = currencyPair; return this; }
    public AnalysisIntent setNewsKeyword(String newsKeyword) { this.newsKeyword = newsKeyword; return this; }
    public AnalysisIntent setTimeframe(String timeframe) { this.timeframe = timeframe; return this; }
    public AnalysisIntent setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; return this; }
    public AnalysisIntent setNeedsMoneyFlow(boolean needsMoneyFlow) { this.needsMoneyFlow = needsMoneyFlow; return this; }

    // ==================== Convenience ====================

    public boolean hasStocks() { return !getStocks().isEmpty(); }
    public boolean hasFutures() { return !getFutures().isEmpty(); }
    public boolean hasFunds() { return !getFunds().isEmpty(); }
    public boolean hasIndices() { return !getIndices().isEmpty(); }
    public boolean needsExchangeRate() { return currencyPair != null && !currencyPair.isBlank(); }
    public boolean needsNews() { return newsKeyword != null && !newsKeyword.isBlank(); }
    public boolean isEmpty() {
        return !hasStocks() && !hasFutures() && !hasFunds() && !hasIndices()
                && !needsExchangeRate() && !needsNews();
    }

    @Override
    public String toString() {
        return "AnalysisIntent{type=" + type + ", stocks=" + stocks + ", futures=" + futures
                + ", funds=" + funds + ", indices=" + indices + ", timeframe=" + timeframe + "}";
    }
}
