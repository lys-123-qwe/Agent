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
public class PhoneLocationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneLocationService.class);

    private static final String FREE_PHONE_API = "https://tenapi.cn/v2/phone";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public PhoneLocationService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String lookup(String phone) {
        String phoneKey = apiConfig.getPhoneApiKey();
        if (phoneKey != null && !phoneKey.isBlank()) {
            try {
                return lookupWithPaidApi(phoneKey, phone);
            } catch (Exception e) {
                log.warn("付费手机归属地API失败，降级到免费API: {}", e.getMessage());
            }
        }
        return lookupWithFreeApi(phone);
    }

    private String lookupWithPaidApi(String phoneKey, String phone) {
        String url = apiConfig.getPhoneApiUrl()
            + "?key=" + phoneKey
            + "&phone=" + phone;

        log.info("手机归属地API请求: {}", url.replace(phoneKey, "***"));

        String response = restClient.get()
            .uri(URI.create(url))
            .retrieve()
            .body(String.class);

        log.info("手机归属地API响应: {}", response);

        JsonNode root = objectMapper.readTree(response);
        int errorCode = root.path("error_code").asInt();
        if (errorCode != 0) {
            String reason = root.path("reason").asText("未知错误");
            throw new RuntimeException("付费API错误: " + reason);
        }

        JsonNode result = root.path("result");
        return formatPhoneResult(
            result.path("province").asText("-"),
            result.path("city").asText("-"),
            result.path("areacode").asText("-"),
            result.path("zip").asText("-"),
            result.path("company").asText("-")
        );
    }

    private String lookupWithFreeApi(String phone) {
        try {
            String url = FREE_PHONE_API + "?phone=" + phone;
            log.info("免费手机归属地API请求: {}", url);

            String response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);

            log.info("免费手机归属地API响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt() != 200) {
                return "手机归属地查询失败: " + root.path("msg").asText("未知错误");
            }

            JsonNode data = root.path("data");
            return formatPhoneResult(
                data.path("province").asText("-"),
                data.path("city").asText("-"),
                data.path("areacode").asText("-"),
                data.path("zip").asText("-"),
                data.path("operator").asText("-")
            );

        } catch (Exception e) {
            log.error("免费手机归属地API失败", e);
            return "手机归属地查询失败: " + e.getMessage();
        }
    }

    private String formatPhoneResult(String province, String city, String areacode, String zip, String company) {
        StringBuilder sb = new StringBuilder();
        sb.append("📱 手机归属地信息\n\n");
        sb.append("📍 省份: ").append(province).append("\n");
        sb.append("🏙️ 城市: ").append(city).append("\n");
        sb.append("📞 区号: ").append(areacode).append("\n");
        sb.append("📮 邮编: ").append(zip).append("\n");
        sb.append("🏢 运营商: ").append(formatCompany(company));
        return sb.toString();
    }

    private String formatCompany(String company) {
        return switch (company) {
            case "中国移动" -> "中国移动 📶";
            case "中国联通" -> "中国联通 📶";
            case "中国电信" -> "中国电信 📶";
            case "中国广电" -> "中国广电 📶";
            default -> company;
        };
    }
}
