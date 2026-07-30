package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class FinancialDataService {

    private static final Logger log = LoggerFactory.getLogger(FinancialDataService.class);
    private static final String EASTMONEY_URL =
        "https://datacenter-web.eastmoney.com/api/data/v1/get";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FinancialDataService() {
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public record FinancialData(
        String roe, String grossMargin, String revenue, String netProfit,
        String eps, String reportDate
    ) {}

    public FinancialData fetch(String stockCode) {
        // 去掉前缀，只留 6 位数字
        String code = stockCode.replaceAll("(?i)^(sh|sz|gb_)", "");
        if (!code.matches("\\d{6}")) return null;

        try {
            String filter = "(SECURITY_CODE=\"" + code + "\")";
            String url = EASTMONEY_URL
                + "?reportName=RPT_F10_FINANCE_MAINFINADATA"
                + "&columns=SECURITY_CODE,REPORT_DATE,BASIC_EPS,WEIGHTAVG_ROE,GROSS_PROFIT_MARGIN,TOTAL_OPERATE_INCOME,PARENT_NETPROFIT"
                + "&filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8)
                + "&pageNumber=1&pageSize=1&sortTypes=-1&sortColumns=REPORT_DATE";

            log.debug("财报请求: {}", url);

            byte[] respBytes = restClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .body(byte[].class);

            if (respBytes == null) return null;
            String text = new String(respBytes, StandardCharsets.UTF_8);

            JsonNode root = objectMapper.readTree(text);
            JsonNode list = root.path("result").path("data");
            if (!list.isArray() || list.isEmpty()) return null;

            JsonNode item = list.get(0);
            String reportDate = item.path("REPORT_DATE").asText("");
            String eps = formatNum(item.path("BASIC_EPS"));
            String roe = formatPct(item.path("WEIGHTAVG_ROE"));
            String grossMargin = formatPct(item.path("GROSS_PROFIT_MARGIN"));
            String revenue = formatAmount(item.path("TOTAL_OPERATE_INCOME"));
            String netProfit = formatAmount(item.path("PARENT_NETPROFIT"));

            log.debug("财报数据: code={}, roe={}, 毛利率={}, 营收={}, 净利={}",
                code, roe, grossMargin, revenue, netProfit);

            return new FinancialData(roe, grossMargin, revenue, netProfit, eps, reportDate);
        } catch (Exception e) {
            log.warn("财报接口失败: code={}, {}", code, e.getMessage());
            return null;
        }
    }

    public String formatSummary(String stockName, FinancialData fd) {
        if (fd == null) return "";
        return String.format(
            "\n\n📋 基本面（%s报）\n  ROE: %s\n  毛利率: %s\n  营收: %s\n  净利润: %s\n  EPS: %s",
            fd.reportDate().substring(0, 4), fd.roe(), fd.grossMargin(),
            fd.revenue(), fd.netProfit(), fd.eps());
    }

    public String formatOneLine(String stockName, FinancialData fd) {
        if (fd == null) return "";
        return String.format("ROE %s 毛利率 %s 营收 %s",
            fd.roe(), fd.grossMargin(), fd.revenue());
    }

    private String formatNum(JsonNode node) {
        if (node == null || node.isNull()) return "—";
        double v = node.asDouble();
        return String.format("%.2f", v);
    }

    private String formatPct(JsonNode node) {
        if (node == null || node.isNull()) return "—";
        double v = node.asDouble();
        return String.format("%.1f%%", v * 100);
    }

    private String formatAmount(JsonNode node) {
        if (node == null || node.isNull()) return "—";
        double v = node.asDouble();
        if (v >= 1e12) return String.format("%.2f万亿", v / 1e12);
        if (v >= 1e8) return String.format("%.2f亿", v / 1e8);
        if (v >= 1e4) return String.format("%.2f万", v / 1e4);
        return String.format("%.2f", v);
    }
}
