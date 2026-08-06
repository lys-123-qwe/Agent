package com.example.youkeda_project.service.assist;

import com.example.youkeda_project.config.ApiConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 物流快递查询服务.
 * <p>
 * 基于阿里云市场「全国快递查询」API(杭州快证签,kzexpress),
 * 支持 1000+ 快递公司自动识别与物流轨迹查询.
 */

@Service
public class LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiConfig apiConfig;

    public LogisticsService(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
    }

    /**
     * 查询快递物流信息.
     *
     * @param trackingNumber 快递单号
     * @param courierName    快递公司名称(可选,API 会自动识别)
     * @return 格式化后的物流信息
     */
    public String query(String trackingNumber, String courierName) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return "错误:请输入快递单号";
        }
        trackingNumber = trackingNumber.trim();

        // 顺丰单号特殊提示
        String phoneHint = "";
        String upper = trackingNumber.toUpperCase();
        if (upper.startsWith("SF")) {
            phoneHint = "\n💡 顺丰快递需提供收/寄件人手机号后四位,下次查顺丰时可以一起发给我,如:查快递 SF1234567890 手机尾号1234";
        }

        log.info("查询物流: number={}, courier={}", trackingNumber, courierName);

        try {
            return doQuery(trackingNumber) + phoneHint;
        } catch (Exception e) {
            log.error("物流查询失败: {}", e.getMessage(), e);
            return "物流查询失败: " + e.getMessage();
        }
    }

    private String doQuery(String trackingNumber) throws Exception {
        String appCode = apiConfig.getExpressApiAppCode();
        if (appCode == null || appCode.isBlank()) {
            throw new RuntimeException("未配置快递查询 AppCode,请在 application.properties 中设置 express.api.appcode");
        }

        // 构造 form-urlencoded body
        StringBuilder body = new StringBuilder();
        body.append("expressNo=").append(URLEncoder.encode(trackingNumber, StandardCharsets.UTF_8));

        log.info("快递查询请求: POST {} body={}", apiConfig.getExpressApiUrl(), body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiConfig.getExpressApiUrl()))
                .header("Authorization", "APPCODE " + appCode)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("快递查询响应[{}]: {}", response.statusCode(), response.body());

        if (response.statusCode() == 403) {
            throw new RuntimeException("API 调用次数用完或 AppCode 无效");
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("查询失败,HTTP 状态码: " + response.statusCode());
        }

        return parseResponse(response.body());
    }

    private String parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            boolean success = root.path("success").asBoolean(false);
            int code = root.path("code").asInt(-1);
            String msg = root.path("msg").asText("");

            if (!success || code != 200) {
                return "查询失败: " + (msg.isEmpty() ? "未知错误 (code=" + code + ")" : msg);
            }

            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return "查询失败: 返回数据为空";
            }

            return formatResult(data);

        } catch (Exception e) {
            log.error("解析物流响应失败: {}", e.getMessage(), e);
            return "解析物流信息失败";
        }
    }

    private String formatResult(JsonNode data) {
        String mailNo = data.path("mailNo").asText("");
        String companyName = data.path("logisticsCompanyName").asText("");
        String statusDesc = data.path("logisticsStatusDesc").asText("");
        String lastMessage = data.path("theLastMessage").asText("");
        String lastTime = data.path("theLastTime").asText("");
        String cpMobile = data.path("cpMobile").asText("");
        String cpUrl = data.path("cpUrl").asText("");

        StringBuilder sb = new StringBuilder();
        sb.append("📦 快递单号:").append(mailNo).append("\n");
        sb.append("快递公司:").append(companyName).append("\n");

        // 物流状态
        if (!statusDesc.isEmpty()) {
            String emoji = switch (statusDesc) {
                case "运输中" -> "🚚";
                case "派送中" -> "📬";
                case "已签收" -> "✅";
                case "已揽收", "已揽件" -> "📥";
                case "派送失败" -> "⚠️";
                default -> "📍";
            };
            sb.append("当前状态:").append(emoji).append(" ").append(statusDesc).append("\n");
        }
        if (!lastMessage.isEmpty()) {
            sb.append("最新动态:").append(lastMessage).append("\n");
        }
        if (!lastTime.isEmpty()) {
            sb.append("更新时间:").append(lastTime).append("\n");
        }
        if (!cpMobile.isEmpty()) {
            sb.append("客服电话:").append(cpMobile);
            if (!cpUrl.isEmpty()) sb.append("(").append(cpUrl).append(")");
            sb.append("\n");
        }

        sb.append("━━━━━━━━━━━━━━\n");

        // 物流轨迹
        JsonNode traceList = data.path("logisticsTraceDetailList");
        if (traceList.isMissingNode() || traceList.isNull() || traceList.isEmpty()) {
            sb.append("暂无物流轨迹\n");
        } else {
            for (JsonNode item : traceList) {
                String desc = item.path("desc").asText("");
                String areaName = item.path("areaName").asText("");
                // 优先用 API 格式化好的 timeDesc,降级到 time 毫秒时间戳
                String timeDesc = item.path("timeDesc").asText("");
                long timeMs = item.path("time").asLong(0);

                String timeStr;
                if (!timeDesc.isEmpty()) {
                    timeStr = timeDesc;
                } else if (timeMs > 0) {
                    timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneId.systemDefault())
                            .format(TIME_FMT);
                } else {
                    timeStr = "";
                }

                StringBuilder line = new StringBuilder();
                line.append("▸ ");
                if (!areaName.isEmpty()) line.append("[").append(areaName).append("] ");
                line.append(desc);
                sb.append(line).append("\n");
                if (!timeStr.isEmpty()) {
                    sb.append("  ").append(timeStr).append("\n");
                }
            }
        }

        return sb.toString();
    }
}
