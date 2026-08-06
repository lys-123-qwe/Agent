package com.example.youkeda_project.ai.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 金融数据聚合容器。
 * <p>
 * 由 FinDataService 并行采集后填充，
 * 通过 toContextText() 拼接为 LLM 可读的上下文文本。
 */

public class FinDataBundle {

    private final Map<String, String> stockResults = new LinkedHashMap<>();
    private final Map<String, String> futuresResults = new LinkedHashMap<>();
    private final Map<String, String> fundResults = new LinkedHashMap<>();
    private final Map<String, String> indexResults = new LinkedHashMap<>();
    private String exchangeRateResult;
    private String newsResult;
    private String moneyFlowResult;

    /** 采集是否发生过错误 */
    private final java.util.List<String> errors = new java.util.ArrayList<>();

    // ==================== 添加数据 ====================

    public void addStockResult(String name, String text) {
        stockResults.put(name, text);
    }

    public void addFuturesResult(String name, String text) {
        futuresResults.put(name, text);
    }

    public void addFundResult(String name, String text) {
        fundResults.put(name, text);
    }

    public void addIndexResult(String name, String text) {
        indexResults.put(name, text);
    }

    public void addError(String error) {
        errors.add(error);
    }

    // ==================== Setters ====================

    public void setExchangeRateResult(String exchangeRateResult) {
        this.exchangeRateResult = exchangeRateResult;
    }

    public void setNewsResult(String newsResult) {
        this.newsResult = newsResult;
    }

    public void setMoneyFlowResult(String moneyFlowResult) {
        this.moneyFlowResult = moneyFlowResult;
    }

    // ==================== Getters ====================

    public Map<String, String> getStockResults() { return stockResults; }
    public Map<String, String> getFuturesResults() { return futuresResults; }
    public Map<String, String> getFundResults() { return fundResults; }
    public Map<String, String> getIndexResults() { return indexResults; }
    public String getExchangeRateResult() { return exchangeRateResult; }
    public String getNewsResult() { return newsResult; }
    public String getMoneyFlowResult() { return moneyFlowResult; }
    public java.util.List<String> getErrors() { return errors; }

    public boolean isEmpty() {
        return stockResults.isEmpty() && futuresResults.isEmpty()
                && fundResults.isEmpty() && indexResults.isEmpty()
                && exchangeRateResult == null && newsResult == null;
    }

    // ==================== 拼接上下文 ====================

    /**
     * 将所有采集数据拼接为结构化上下文文本，供 LLM 分析使用。
     */
    public String toContextText() {
        StringBuilder sb = new StringBuilder();

        if (!indexResults.isEmpty()) {
            sb.append("【大盘指数】\n");
            for (var entry : indexResults.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(":\n");
                sb.append(indent(entry.getValue(), 4)).append("\n");
            }
        }

        if (!futuresResults.isEmpty()) {
            sb.append("【期货行情】\n");
            for (var entry : futuresResults.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(":\n");
                sb.append(indent(entry.getValue(), 4)).append("\n");
            }
        }

        if (!stockResults.isEmpty()) {
            sb.append("【股票行情】\n");
            for (var entry : stockResults.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(":\n");
                sb.append(indent(entry.getValue(), 4)).append("\n");
            }
        }

        if (!fundResults.isEmpty()) {
            sb.append("【基金行情】\n");
            for (var entry : fundResults.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(":\n");
                sb.append(indent(entry.getValue(), 4)).append("\n");
            }
        }

        if (exchangeRateResult != null && !exchangeRateResult.isBlank()) {
            sb.append("【汇率数据】\n");
            sb.append("  ").append(exchangeRateResult).append("\n\n");
        }

        if (newsResult != null && !newsResult.isBlank()) {
            sb.append("【相关资讯】\n");
            sb.append(indent(newsResult, 2)).append("\n\n");
        }

        if (moneyFlowResult != null && !moneyFlowResult.isBlank()) {
            sb.append("【资金面数据】\n");
            sb.append(indent(moneyFlowResult, 2)).append("\n\n");
        }

        if (!errors.isEmpty()) {
            sb.append("【数据采集提示】部分数据获取失败:\n");
            for (String err : errors) {
                sb.append("  - ").append(err).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String indent(String text, int spaces) {
        String prefix = " ".repeat(spaces);
        return prefix + text.replace("\n", "\n" + prefix);
    }
}
