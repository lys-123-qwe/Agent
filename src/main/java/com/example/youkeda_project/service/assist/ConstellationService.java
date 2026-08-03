package com.example.youkeda_project.service.assist;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Random;

@Service
public class ConstellationService {

    private static final Logger log = LoggerFactory.getLogger(ConstellationService.class);

    private static final List<ZodiacSign> ZODIAC_SIGNS = List.of(
        new ZodiacSign("白羊座", MonthDay.of(3, 21), MonthDay.of(4, 19)),
        new ZodiacSign("金牛座", MonthDay.of(4, 20), MonthDay.of(5, 20)),
        new ZodiacSign("双子座", MonthDay.of(5, 21), MonthDay.of(6, 21)),
        new ZodiacSign("巨蟹座", MonthDay.of(6, 22), MonthDay.of(7, 22)),
        new ZodiacSign("狮子座", MonthDay.of(7, 23), MonthDay.of(8, 22)),
        new ZodiacSign("处女座", MonthDay.of(8, 23), MonthDay.of(9, 22)),
        new ZodiacSign("天秤座", MonthDay.of(9, 23), MonthDay.of(10, 23)),
        new ZodiacSign("天蝎座", MonthDay.of(10, 24), MonthDay.of(11, 21)),
        new ZodiacSign("射手座", MonthDay.of(11, 22), MonthDay.of(12, 21)),
        new ZodiacSign("摩羯座", MonthDay.of(12, 22), MonthDay.of(1, 19)),
        new ZodiacSign("水瓶座", MonthDay.of(1, 20), MonthDay.of(2, 18)),
        new ZodiacSign("双鱼座", MonthDay.of(2, 19), MonthDay.of(3, 20))
    );

    private static final String[] HOROSCOPE_ADVICE = {
        "今天适合主动出击,抓住机会展现自己.",
        "保持耐心,好事多磨,下午会有转机.",
        "适合与人沟通交流,可能会有意外收获.",
        "今天要注意细节,小失误可能带来大麻烦.",
        "运势平稳,适合处理积压已久的任务.",
        "财运不错,但投资仍需谨慎.",
        "感情方面有惊喜,单身者有望脱单.",
        "工作压力较大,注意劳逸结合.",
        "今天适合学习新知识,提升自己.",
        "人际关系运佳,适合社交活动.",
        "健康方面需注意,不要熬夜.",
        "今天适合做决定,直觉准确.",
        "财运上升,但避免冲动消费.",
        "事业上会遇到贵人,把握机会.",
        "今天适合独处,整理思绪.",
        "好运降临,之前努力的事情会有结果."
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public ConstellationService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
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

                var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .GET()
                    .build();
                HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
                String queryResp = hresp.body();

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
                log.warn("星座查询API调用失败,降级到本地计算", e);
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
        if (horoscopeKey != null && !horoscopeKey.isBlank()) {
            try {
                String url = apiConfig.getConstellationHoroscopeUrl()
                    + "?key=" + horoscopeKey
                    + "&consName=" + URLEncoder.encode(sign, StandardCharsets.UTF_8)
                    + "&type=today";

                log.info("星座运势API请求: {}", url.replace(horoscopeKey, "***"));

                var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
                String response = hresp.body();

                log.info("星座API响应: {}", response);

                JsonNode root = objectMapper.readTree(response);
                int errorCode = root.path("error_code").asInt();
                if (errorCode == 0) {
                    return formatHoroscopeApiResult(root);
                }
                log.warn("星座API返回错误: {}", root.path("reason").asText());
            } catch (Exception e) {
                log.warn("星座API调用失败,降级到本地生成", e);
            }
        }

        return generateLocalHoroscope(sign);
    }

    private String formatHoroscopeApiResult(JsonNode root) {
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
    }

    private String generateLocalHoroscope(String sign) {
        LocalDate today = LocalDate.now();
        long seed = (long) sign.hashCode() * 31 + (long) today.getYear() * 365 + today.getDayOfYear();
        Random rng = new Random(seed);

        int allScore = rng.nextInt(5) + 1;
        int loveScore = rng.nextInt(5) + 1;
        int workScore = rng.nextInt(5) + 1;
        int moneyScore = rng.nextInt(5) + 1;
        int healthScore = rng.nextInt(5) + 1;
        int luckyNum = rng.nextInt(49) + 1;
        String luckyColor = pickRandom(rng, "红色", "蓝色", "绿色", "黄色", "紫色", "白色", "黑色", "橙色", "粉色", "金色");
        String compatibleSign = pickRandom(rng, ZODIAC_SIGNS.stream().map(z -> z.name).filter(n -> !n.equals(sign)).toArray(String[]::new));
        String advice = HOROSCOPE_ADVICE[rng.nextInt(HOROSCOPE_ADVICE.length)];

        StringBuilder sb = new StringBuilder();
        sb.append("🌟 **").append(sign).append(" ").append(today).append(" 运势**\n\n");
        sb.append(advice).append("\n\n");
        sb.append("📊 综合运势: ").append(scoreToStars(allScore)).append("\n");
        sb.append("❤️ 爱情运势: ").append(scoreToStars(loveScore)).append("\n");
        sb.append("💼 事业运势: ").append(scoreToStars(workScore)).append("\n");
        sb.append("💰 财运运势: ").append(scoreToStars(moneyScore)).append("\n");
        sb.append("🏥 健康运势: ").append(scoreToStars(healthScore)).append("\n\n");
        sb.append("🔢 幸运数字: ").append(luckyNum).append("\n");
        sb.append("🎨 幸运色: ").append(luckyColor).append("\n");
        sb.append("🤝 速配星座: ").append(compatibleSign);
        return sb.toString();
    }

    private String scoreToStars(int score) {
        return "★".repeat(score) + "☆".repeat(5 - score) + " " + score + "/5";
    }

    @SuppressWarnings("unchecked")
    private <T> T pickRandom(Random rng, T... items) {
        return items[rng.nextInt(items.length)];
    }

    private String getZodiacSign(int month, int day) {
        MonthDay md = MonthDay.of(month, day);
        for (ZodiacSign z : ZODIAC_SIGNS) {
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
