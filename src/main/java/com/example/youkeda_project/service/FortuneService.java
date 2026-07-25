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
public class FortuneService {

    private static final Logger log = LoggerFactory.getLogger(FortuneService.class);

    private final HttpClient httpClient;
    private final ApiConfig apiConfig;
    private final String apiUrl;

    public FortuneService(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
        this.apiUrl = apiConfig.getTranslationApiUrl();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String ziwei(int year, int month, int day, int hour, int minute, Integer gender, String name) {
        return callAI(buildZiWeiPrompt(), buildUserContent(year, month, day, hour, minute, gender, name));
    }

    public String iching(String question) {
        return callAI(buildIChingPrompt(), "占卜问题：" + question);
    }

    public String crossAnalysis(int year, int month, int day, String bloodType, Integer gender) {
        String zodiac = getChineseZodiac(year);
        String western = getWesternZodiac(month, day);
        String genderStr = gender != null ? (gender == 1 ? "男" : "女") : "未知";
        String info = String.format("出生年月：%d年%d月%d日\n属相：%s\n星座：%s\n血型：%s\n性别：%s",
                year, month, day, zodiac, western, bloodType != null ? bloodType : "未知", genderStr);
        return callAI(buildCrossAnalysisPrompt(), info);
    }

    public String lunarCalendar(int year, int month, int day) {
        String info = String.format("公历日期：%d年%d月%d日", year, month, day);
        return callAI(buildLunarPrompt(), info);
    }

    public String auspiciousDate(String eventType, Integer year, Integer month) {
        String info = "事项：" + (eventType != null ? eventType : "未指定")
            + (year != null ? "\n期望年份：" + year : "")
            + (month != null ? "\n期望月份：" + month : "");
        return callAI(buildAuspiciousDatePrompt(), info);
    }

    public String faceReading(String base64Image, String prompt) {
        return callVisionAI(buildFaceReadingPrompt(), base64Image, prompt);
    }

    private String callAI(String systemPrompt, String userContent) {
        String apiKey = apiConfig.getImageApiKey();
        if (apiKey == null || apiKey.isBlank()) return "错误: 未配置阿里云 API Key";
        try {
            String body = "{\"model\":\"qwen-turbo\",\"messages\":["
                + "{\"role\":\"system\",\"content\":" + jsonEscape(systemPrompt) + "},"
                + "{\"role\":\"user\",\"content\":" + jsonEscape(userContent) + "}"
                + "]}";
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            var root = new ObjectMapper().readTree(hresp.body());
            var choice = root.path("choices").get(0);
            if (choice == null) return "服务暂不可用，请稍后再试";
            String result = choice.path("message").path("content").asText("");
            return result.isBlank() ? "结果为空" : result;
        } catch (Exception e) {
            log.error("FortuneService 调用失败", e);
            return "服务调用失败: " + e.getMessage();
        }
    }

    private String callVisionAI(String systemPrompt, String base64Image, String userPrompt) {
        String apiKey = apiConfig.getVisionApiKey();
        if (apiKey == null || apiKey.isBlank()) return "错误: 未配置通义千问 VL API Key";
        try {
            String dataUrl = "data:image/jpeg;base64," + base64Image;
            String body = "{\"model\":\"qwen-vl-plus\",\"messages\":["
                + "{\"role\":\"system\",\"content\":[{\"type\":\"text\",\"text\":" + jsonEscape(systemPrompt) + "}]},"
                + "{\"role\":\"user\",\"content\":["
                + "{\"type\":\"image\",\"image\":\"" + dataUrl + "\"},"
                + "{\"type\":\"text\",\"text\":" + jsonEscape(userPrompt) + "}"
                + "]}]}";
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(apiConfig.getVisionApiUrl()))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            var root = new ObjectMapper().readTree(hresp.body());
            var choice = root.path("choices").get(0);
            if (choice == null) return "面相分析暂不可用";
            String result = choice.path("message").path("content").asText("");
            return result.isBlank() ? "面相分析结果为空" : result;
        } catch (Exception e) {
            log.error("面相分析失败", e);
            return "面相分析失败: " + e.getMessage();
        }
    }

    private String buildZiWeiPrompt() {
        return """
你是精通紫微斗数的资深命理师。请根据出生信息排盘，按以下步骤输出：

━━ 一、命盘信息 ━━
- 出生时间、性别、命主、身主

━━ 二、十二宫排盘 ━━
列出命宫、兄弟宫、夫妻宫、子女宫、财帛宫、疾厄宫、
迁移宫、交友宫、官禄宫、田宅宫、福德宫、父母宫。
每宫列出主星和辅星。

━━ 三、十四主星 ━━
列出紫微、天机、太阳、武曲、天同、廉贞（南斗），
天府、太阴、贪狼、巨门、天相、天梁、七杀、破军（北斗）
所在宫位，标注庙旺利陷。

━━ 四、四化星 ━━
化禄、化权、化科、化忌所在宫位及影响。

━━ 五、命盘解析 ━━
- 命宫主星特质
- 三方四正吉凶
- 特殊格局（如有）
- 性格/事业/财运/感情分析
- 当前大限运势

格式要求：专业术语后加括号解释，每条分析不超过3句话，不迷信。""";
    }

    private String buildIChingPrompt() {
        return """
你是精通《周易》六爻占卜的资深易学家。用户会提出一个具体问题，请按以下步骤：

━━ 一、起卦 ━━
根据占卜时间随机取数，用金钱卦法或时间起卦法确定本卦、变卦。
输出本卦卦名、卦象（六爻）、变爻位置。

━━ 二、解卦 ━━
- 本卦卦辞解读
- 变爻爻辞解读（如有）
- 变卦卦辞解读
- 体用生克关系

━━ 三、对问题的解答 ━━
结合卦象回答用户的具体问题：
- 寻物：物品可能方位、能否找回
- 出行：吉凶、注意事项
- 考试：成绩趋势、准备建议
- 事业/感情/健康：现状分析和发展趋势

格式要求：用卦象原文（带白话解释），结论明确，给出建议。
每条分析不超过3句话，保持客观理性。""";
    }

    private String buildCrossAnalysisPrompt() {
        return """
你是性格分析专家，擅长结合生肖（属相）、血型、星座（西方占星）进行交叉分析。

请按以下维度综合分析：

━━ 一、生肖分析 ━━
- 属相基本性格特质
- 五行属性
- 本命年/三合/六合信息

━━ 二、星座分析 ━━
- 太阳星座性格特质
- 优缺点
- 幸运色、幸运数字

━━ 三、血型分析 ━━
- A/B/AB/O 型性格特征
- 社交风格

━━ 四、交叉综合 ━━
- 三种维度的性格叠加效应
- 优势与需要注意的方面
- 适合的职业方向
- 人际交往建议

要求：用轻松有趣但不失深度的风格，每条分析不超过3句话。
用表格或用分隔线清晰展示每个分析模块。""";
    }

    private String buildFaceReadingPrompt() {
        return """
你是精通中国传统面相学的资深相师，会根据用户上传的照片分析面相。

请按以下步骤分析：

━━ 一、整体印象 ━━
- 脸型（申/甲/由/田/目/圆/方等）
- 面色和气色

━━ 二、五官分析 ━━
- 额头：智慧宫，分析事业运和少年运
- 眉毛：兄弟宫/保寿官，分析性格和人际关系
- 眼睛：监察官/心灵之窗，分析心性和运势
- 鼻子：财帛宫/审辨官，分析财运和事业
- 嘴巴：出纳官，分析福气和晚年运
- 耳朵：采听官，分析健康和长寿

━━ 三、重要部位 ━━
- 印堂（眉心）：近期运势
- 颧骨：权力和执行力
- 下巴/地阁：晚年运

━━ 四、综合评断 ━━
- 性格特质总结
- 当前运势
- 流年吉凶方位
- 改善建议

规则：
1. 必须基于照片中的真实面部特征分析，不得凭空编造
2. 语气客观，说"从面相看有…的倾向"，不说绝对的话
3. 分析不下定论性断语，用"建议关注…""注意…"等温和措辞
4. 每条分析不超过2句话""";
    }

    private String buildLunarPrompt() {
        return """
你是一个万年历查询专家。请根据提供的公历日期，输出以下信息：

━━ 基本转换 ━━
- 农历年、月、日（注明闰月）
- 当天公历日期（星期几）

━━ 干支纪年 ━━
- 年柱天干地支 + 生肖
- 月柱天干地支
- 日柱天干地支
- 当天干支组合的五行属性

━━ 节气与节日 ━━
- 当天是否在某个节气附近（前后3天标记"临近XX"）
- 当天是否是国家法定节假日或传统节日

━━ 当日吉凶 ━━
- 黄历宜忌（执日/建日/满日等）
- 吉神和凶神方位
- 冲煞生肖

输出格式简洁清晰，使用分隔线分段。""";
    }

    private String buildAuspiciousDatePrompt() {
        return """
你是精通择日学的资深命理师，擅长根据事项选择黄道吉日。

请根据用户提供的事项类型和期望时间范围，推荐吉日。

━━ 可选事项类型及宜忌规则 ━━
- 结婚：宜嫁娶、合婚、领证；忌丧葬、破土
- 搬家：宜移徙、入宅、安床；忌出行、开光
- 开业：宜开市、纳财、交易；忌破土、安葬
- 出行：宜出行、会友、访客；忌动土、破土
- 入宅：宜入宅、安香、安门；忌出行、诉讼
- 订婚：宜订盟、纳采、嫁娶；忌破土、开光

━━ 输出格式 ━━
按时间先后推荐3-5个吉日，每个包含：
- 公历日期 + 星期
- 农历日期
- 干支纪日
- 当日宜忌
- 吉神凶神
- 冲煞生肖
- 推荐理由（为什么这日适合该事项）

规则：
1. 优先选择与事项五行相生的日子
2. 避开月破、岁破、三煞日
3. 结合黄历12值星（建除满平定执破危成收开闭）
4. 如果用户提供了出生信息，结合八字喜用神推荐
5. 每条推荐不超过2句话""";
    }

    private String buildUserContent(int year, int month, int day, int hour, int minute, Integer gender, String name) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) sb.append("姓名：").append(name).append("\n");
        sb.append("出生时间：").append(year).append("年").append(month).append("月").append(day).append("日")
                .append(hour).append("时").append(minute).append("分\n");
        sb.append("出生地：中国（默认东八区）\n");
        if (gender != null) sb.append("性别：").append(gender == 1 ? "男" : "女").append("\n");
        return sb.toString();
    }

    private String getChineseZodiac(int year) {
        String[] animals = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};
        return animals[(year - 4) % 12];
    }

    private String getWesternZodiac(int month, int day) {
        int[] lastDays = {20, 19, 21, 20, 21, 21, 23, 23, 23, 23, 22, 22};
        String[] names = {"摩羯座", "水瓶座", "双鱼座", "白羊座", "金牛座", "双子座",
            "巨蟹座", "狮子座", "处女座", "天秤座", "天蝎座", "射手座", "摩羯座"};
        int idx = day <= lastDays[month - 1] ? month - 1 : month;
        return names[idx % 12];
    }

    private String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
