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
        if (phoneKey == null || phoneKey.isBlank()) {
            return "错误: 未配置手机归属地 API Key";
        }

        try {
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
                return "手机归属地查询失败: " + reason;
            }

            JsonNode result = root.path("result");

            StringBuilder sb = new StringBuilder();
            sb.append("📱 手机归属地信息\n\n");
            sb.append("📍 省份: ").append(result.path("province").asText("-")).append("\n");
            sb.append("🏙️ 城市: ").append(result.path("city").asText("-")).append("\n");
            sb.append("📞 区号: ").append(result.path("areacode").asText("-")).append("\n");
            sb.append("📮 邮编: ").append(result.path("zip").asText("-")).append("\n");
            sb.append("🏢 运营商: ").append(formatCompany(result.path("company").asText("-")));

            return sb.toString();

        } catch (Exception e) {
            log.error("手机归属地查询失败", e);
            return "手机归属地查询失败: " + e.getMessage();
        }
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
