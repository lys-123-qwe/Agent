package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ConstellationService {

    private static final Logger log = LoggerFactory.getLogger(ConstellationService.class);

    private List<ZodiacSign> zodiacSigns;

    private List<ZodiacSign> getZodiacSigns() {
        if (zodiacSigns == null) {
            List<Map<String, Object>> config = apiConfig.getConstellationZodiacs();
            List<ZodiacSign> list = new ArrayList<>();
            for (Map<String, Object> item : config) {
                list.add(new ZodiacSign(
                    (String) item.get("name"),
                    MonthDay.of((Integer) item.get("startMonth"), (Integer) item.get("startDay")),
                    MonthDay.of((Integer) item.get("endMonth"), (Integer) item.get("endDay"))
                ));
            }
            zodiacSigns = list;
        }
        return zodiacSigns;
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public ConstellationService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String queryByDate(int month, int day) {
        String queryKey = apiConfig.getConstellationQueryKey();
        if (queryKey != null && !queryKey.isBlank()) {
            try {
                String keyword = month + "月" + day + "日";
                String queryUrl = apiConfig.getConstellationQueryUrl()
                    + "?key=" + queryKey
                    + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);

                log.info("星座查询API请求: {}", queryUrl.replace(queryKey, "***"));

                String queryResp = restClient.get()
                    .uri(URI.create(queryUrl))
                    .retrieve()
                    .body(String.class);

                log.info("星座查询API响应: {}", queryResp);

                JsonNode qRoot = objectMapper.readTree(queryResp);
                if (qRoot.path("error_code").asInt() == 0) {
                    JsonNode qResult = qRoot.path("result");
                    String name = qResult.path("name").asText("");
                    if (!name.isBlank()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("📅 ").append(month).append("月").append(day).append("日出生的人是 **").append(name).append("**\n\n");
                        if (qResult.has("date")) {
                            sb.append("📆 日期范围: ").append(qResult.path("date").asText()).append("\n");
                        }
                        if (qResult.has("info")) {
                            sb.append("\n").append(qResult.path("info").asText());
                        }
                        return sb.toString();
                    }
                }
            } catch (Exception e) {
                log.warn("星座查询API调用失败，降级到本地计算", e);
            }
        }

        String zodiac = getZodiacSign(month, day);
        if (zodiac == null) {
            return "无效的日期: " + month + "月" + day + "日";
        }
        return "📅 " + month + "月" + day + "日出生的人是 **" + zodiac + "**";
    }

    public String queryHoroscope(String sign) {
        sign = sign.trim().replace("座", "") + "座";

        String horoscopeKey = apiConfig.getConstellationHoroscopeKey();
        if (horoscopeKey == null || horoscopeKey.isBlank()) {
            return "错误: 未配置星座运势 API Key";
        }

        try {
            String url = apiConfig.getConstellationHoroscopeUrl()
                + "?key=" + horoscopeKey
                + "&consName=" + URLEncoder.encode(sign, StandardCharsets.UTF_8)
                + "&type=today";

            log.info("星座运势API请求: {}", url.replace(horoscopeKey, "***"));

            String response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);

            log.info("星座API响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            int errorCode = root.path("error_code").asInt();
            if (errorCode != 0) {
                return "星座查询失败: " + root.path("reason").asText("未知错误");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🌟 **").append(root.path("name").asText()).append(" ")
                .append(root.path("datetime").asText()).append("运势**\n\n");

            if (root.has("summary")) {
                sb.append(root.path("summary").asText()).append("\n\n");
            }

            sb.append("📊 综合运势: ").append(root.path("all").asText()).append("\n");
            sb.append("❤️ 爱情运势: ").append(root.path("love").asText()).append("\n");
            sb.append("💼 事业运势: ").append(root.path("work").asText()).append("\n");
            sb.append("💰 财运运势: ").append(root.path("money").asText()).append("\n");
            sb.append("🏥 健康运势: ").append(root.path("health").asText()).append("\n\n");

            sb.append("🔢 幸运数字: ").append(root.path("number").asText()).append("\n");
            sb.append("🎨 幸运色: ").append(root.path("color").asText()).append("\n");
            sb.append("🤝 速配星座: ").append(root.path("QFriend").asText());

            return sb.toString();

        } catch (Exception e) {
            log.error("星座查询失败", e);
            return "星座查询失败: " + e.getMessage();
        }
    }

    private String getZodiacSign(int month, int day) {
        MonthDay md = MonthDay.of(month, day);
        for (ZodiacSign z : getZodiacSigns()) {
            if (z.end.getMonthValue() < z.start.getMonthValue()) {
                if (md.getMonthValue() >= z.start.getMonthValue() || md.getMonthValue() <= z.end.getMonthValue()) {
                    return z.name;
                }
            } else if ((md.isAfter(z.start) || md.equals(z.start)) && (md.isBefore(z.end) || md.equals(z.end))) {
                return z.name;
            }
        }
        return null;
    }

    private record ZodiacSign(String name, MonthDay start, MonthDay end) {}
}
