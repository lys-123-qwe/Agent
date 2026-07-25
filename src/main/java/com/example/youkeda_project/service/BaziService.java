package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class BaziService {

    private static final Logger log = LoggerFactory.getLogger(BaziService.class);

    private final HttpClient httpClient;
    private final ApiConfig apiConfig;
    private final String apiUrl;

    public BaziService(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
        this.apiUrl = apiConfig.getTranslationApiUrl();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String calculate(int year, int month, int day, int hour, int minute, Integer gender, String name) {
        String apiKey = apiConfig.getImageApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置阿里云 API Key";
        }

        String systemPrompt = buildPrompt();
        String userContent = buildUserContent(year, month, day, hour, minute, gender, name);

        try {
            String body = "{\"model\":\"qwen-turbo\",\"messages\":["
                + "{\"role\":\"system\",\"content\":" + jsonEscape(systemPrompt) + "},"
                + "{\"role\":\"user\",\"content\":" + jsonEscape(userContent) + "}"
                + "]}";

            log.info("八字推算请求: {}-{}-{}-{}:{}", year, month, day, hour, minute);

            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            String respBody = hresp.body();

            var mapper = new ObjectMapper();
            var root = mapper.readTree(respBody);
            var choice = root.path("choices").get(0);
            if (choice == null) return "算命服务暂不可用，请稍后再试";

            String result = choice.path("message").path("content").asText("");
            if (result.isBlank()) return "算命结果为空";
            return result;
        } catch (Exception e) {
            log.error("八字推算失败", e);
            return "八字推算失败: " + e.getMessage();
        }
    }

    private String buildPrompt() {
        return """
你是一位精通中国传统命理学的资深命理师，擅长子平八字（四柱命理）。

请根据用户提供的出生信息，按以下步骤推算并输出：

━━ 第一步：排八字（四柱） ━━
1. 公历→农历转换（注明闰月）
2. 计算年柱（年干+年支，立春分界）
3. 计算月柱（月干+月支，节气分界）
4. 计算日柱（日干+日支）
5. 计算时柱（时干+时支）
输出格式：X年 月柱 日柱 时柱，每个天干地支标注五行

━━ 第二步：十神分析 ━━
- 日主（日干）是什么
- 年柱、月柱、时柱的十神关系
- 藏干分析

━━ 第三步：五行分析 ━━
- 统计八字中金木水火土的数量
- 判断日主强弱（身强/身弱）
- 喜用神和忌神
- 五行缺失/偏旺分析

━━ 第四步：大运分析 ━━
- 起运年龄和时间
- 排大运（每10年一运）
- 当前所处大运

━━ 第五步：命局总评 ━━
- 性格特质（基于日主+十神）
- 事业财运分析
- 感情婚姻分析
- 健康提示
- 当前流年运势

━━ 第六步：每日宜忌（今日） ━━
- 当前日期的宜忌事项

格式要求：
- 用简洁清晰的分段输出
- 专业术语后加括号解释
- 整体语气客观、严谨、不迷信
- 每条分析不超过3句话""";
    }

    private String buildUserContent(int year, int month, int day, int hour, int minute, Integer gender, String name) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) {
            sb.append("姓名：").append(name).append("\n");
        }
        sb.append("出生时间：").append(year).append("年").append(month).append("月").append(day).append("日")
                .append(hour).append("时").append(minute).append("分\n");
        sb.append("出生地：中国（默认东八区）\n");
        if (gender != null) {
            sb.append("性别：").append(gender == 1 ? "男" : "女").append("\n");
        }
        return sb.toString();
    }

    private String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
