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

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public ExchangeRateService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String convert(String from, String to, double amount) {
        if (apiConfig.getExchangeApiKey() == null || apiConfig.getExchangeApiKey().isBlank()
            || "YOUR_EXCHANGE_RATE_API_KEY".equals(apiConfig.getExchangeApiKey())) {
            return "错误: 未配置汇率 API Key";
        }

        try {
            String url = apiConfig.getExchangeApiUrl() + "/" + apiConfig.getExchangeApiKey() + "/pair/" + from + "/" + to;
            log.info("汇率API请求: {}", url.replace(apiConfig.getExchangeApiKey(), "***"));

            String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

            log.info("汇率API响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            String result = root.path("result").asText();

            if (!"success".equals(result)) {
                String errorType = root.path("error-type").asText("未知错误");
                return "汇率查询失败: " + errorType;
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

        } catch (Exception e) {
            log.error("汇率查询失败", e);
            return "汇率查询失败: " + e.getMessage();
        }
    }
}
