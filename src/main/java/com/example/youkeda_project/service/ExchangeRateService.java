package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private static final String FRANKFURTER_URL = "https://api.frankfurter.app/latest";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public ExchangeRateService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String convert(String from, String to, double amount) {
        String apiKey = apiConfig.getExchangeApiKey();
        if (apiKey != null && !apiKey.isBlank()
            && !"YOUR_EXCHANGE_RATE_API_KEY".equals(apiKey)) {
            try {
                return convertWithPaidApi(apiKey, from, to, amount);
            } catch (Exception e) {
                log.warn("付费汇率API失败，降级到Frankfurter免费API: {}", e.getMessage());
            }
        }
        return convertWithFreeApi(from, to, amount);
    }

    private String convertWithPaidApi(String apiKey, String from, String to, double amount) {
        String url = apiConfig.getExchangeApiUrl() + "/" + apiKey + "/pair/" + from + "/" + to;
        log.info("汇率API请求: {}", url.replace(apiKey, "***"));

        String response = restClient.get()
            .uri(url)
            .retrieve()
            .body(String.class);

        log.info("汇率API响应: {}", response);

        JsonNode root = objectMapper.readTree(response);
        String result = root.path("result").asText();

        if (!"success".equals(result)) {
            String errorType = root.path("error-type").asText("未知错误");
            throw new RuntimeException("汇率API错误: " + errorType);
        }

        double rate = root.path("conversion_rate").asDouble();
        double converted = amount * rate;
        String lastUpdate = root.path("time_last_update_utc").asText("");

        return String.format(
            "%.2f %s = %.2f %s\n汇率: 1 %s = %.6f %s%s",
            amount, from, converted, to,
            from, rate, to,
            lastUpdate.isEmpty() ? "" : "\n更新时间: " + lastUpdate
        );
    }

    private String convertWithFreeApi(String from, String to, double amount) {
        try {
            String url = FRANKFURTER_URL + "?from=" + from + "&to=" + to;
            log.info("Frankfurter免费汇率API请求: {}", url);

            String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

            log.info("Frankfurter响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            double rate = root.path("rates").path(to).asDouble();
            if (rate <= 0) {
                return "汇率查询失败: 不支持的货币对 " + from + "/" + to;
            }

            double converted = amount * rate;
            String date = root.path("date").asText("");

            return String.format(
                "%.2f %s = %.2f %s\n汇率: 1 %s = %.6f %s%s",
                amount, from, converted, to,
                from, rate, to,
                date.isEmpty() ? "" : "\n数据日期: " + date
            );

        } catch (Exception e) {
            log.error("免费汇率API查询失败", e);
            return "汇率查询失败: " + e.getMessage();
        }
    }
}
