package com.example.youkeda_project.service.assist;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
/** 手机号归属地查询。 */


@Service
public class PhoneLocationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneLocationService.class);
    private static final String FREE_API = "https://cx.shouji.360.cn/phonearea.php?number=";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public PhoneLocationService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String lookup(String phone) {
        String phoneKey = apiConfig.getPhoneApiKey();
        if (phoneKey != null && !phoneKey.isBlank()) {
            try {
                String url = apiConfig.getPhoneApiUrl()
                    + "?key=" + phoneKey
                    + "&phone=" + phone;

                log.info("手机归属地API请求: {}", url.replace(phoneKey, "***"));

                var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
                String response = hresp.body();

                log.info("手机归属地API响应: {}", response);

                JsonNode root = objectMapper.readTree(response);
                int errorCode = root.path("error_code").asInt();
                if (errorCode == 0) {
                    JsonNode result = root.path("result");
                    return formatResult(
                        result.path("province").asText("-"),
                        result.path("city").asText("-"),
                        result.path("areacode").asText("-"),
                        result.path("zip").asText("-"),
                        result.path("company").asText("-")
                    );
                }
                log.warn("手机归属地API返回错误: {}", root.path("reason").asText());
            } catch (Exception e) {
                log.warn("手机归属地API调用失败,降级到免费API", e);
            }
        }

        return freeLookup(phone);
    }

    private String freeLookup(String phone) {
        try {
            String url = FREE_API + phone;
            log.info("免费手机归属地查询: {}", url);

            var hreq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            String response = hresp.body();

            log.info("免费API响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt() != 0) {
                return "手机归属地查询失败: " + root.path("msg").asText("未知错误");
            }

            JsonNode data = root.path("data");
            return formatResult(
                data.path("province").asText("-"),
                data.path("city").asText("-"),
                "-",
                "-",
                data.path("sp").asText("-")
            );
        } catch (Exception e) {
            log.error("免费手机归属地查询也失败", e);
            return "手机归属地查询失败: " + e.getMessage();
        }
    }

    private String formatResult(String province, String city, String areacode, String zip, String company) {
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
