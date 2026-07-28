package com.example.youkeda_project.service;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class FortuneService {

    private static final Logger log = LoggerFactory.getLogger(FortuneService.class);
    private static final String YUANFENJU_BASE = "https://api.yuanfenju.com/index.php/v1";
    private static final String GAN = "甲乙丙丁戊己庚辛壬癸";
    private static final String[] GAN_WUXING = {"木","木","火","火","土","土","金","金","水","水"};
    private static final String ZHI = "子丑寅卯辰巳午未申酉戌亥";
    private static final String[] ZHI_WUXING = {"水","土","木","木","土","火","火","土","金","金","土","水"};

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

    public String ziwei(int year, int month, int day, int hour, int minute, Integer gender, String name, int calendarType) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) sb.append("姓名：").append(name).append("\n");
        sb.append(doZiwei(year, month, day, hour, minute, gender, name, calendarType));
        sb.append("\n\n---\n\n📌 数据由缘份居紫微排盘 API 提供");
        return sb.toString();
    }

    public String iching(String question, int[] guaYaos) {
        if (guaYaos == null || guaYaos.length != 6) {
            return "六爻占卜需要 6 次抛硬币结果";
        }
        Map<String, String> params = new HashMap<>();
        params.put("sex", "0");
        params.put("born_year", String.valueOf(LocalDate.now().getYear() - 25));
        params.put("pan_model", "4");
        params.put("gua_yao6", String.valueOf(guaYaos[0]));
        params.put("gua_yao5", String.valueOf(guaYaos[1]));
        params.put("gua_yao4", String.valueOf(guaYaos[2]));
        params.put("gua_yao3", String.valueOf(guaYaos[3]));
        params.put("gua_yao2", String.valueOf(guaYaos[4]));
        params.put("gua_yao1", String.valueOf(guaYaos[5]));
        JsonNode data = callYuanfenjuApi("/Liupan/liuyao", params);
        if (data != null) {
            String result = formatYuanfenjuIChing(data);
            if (result != null) {
                if (question != null && !question.isBlank()) result = "问题：" + question + "\n\n" + result;
                return result + "\n\n---\n\n📌 数据由缘份居六爻排盘 API 提供";
            }
        }
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
        Map<String, String> params = new HashMap<>();
        params.put("title_laohuangli", String.format("%04d-%02d-%02d", year, month, day));
        JsonNode data = callYuanfenjuApi("/Gongju/laohuangli", params);
        if (data != null) {
            String result = formatYuanfenjuLunar(data);
            if (result != null) return result;
        }
        String info = String.format("公历日期：%d年%d月%d日", year, month, day);
        return "[AI生成] " + callAI(buildLunarPrompt(), info);
    }

    public String auspiciousDate(String eventType, Integer year, Integer month) {
        int incident = mapEventTypeToIncident(eventType);
        Map<String, String> params = new HashMap<>();
        params.put("future", year != null && month != null ? "2" : "0");
        params.put("incident", String.valueOf(incident));
        JsonNode data = callYuanfenjuApi("/Gongju/zeshi", params);
        if (data != null) {
            String result = formatYuanfenjuAuspiciousDate(data, eventType);
            if (result != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("## 选吉日：").append(eventType).append("\n\n");
                sb.append(result);
                sb.append("\n\n---\n\n📌 数据由缘份居择吉日 API 提供");
                return sb.toString();
            }
        }
        String info = "事项：" + (eventType != null ? eventType : "未指定")
            + (year != null ? "\n期望年份：" + year : "")
            + (month != null ? "\n期望月份：" + month : "");
        return "[AI生成] " + callAI(buildAuspiciousDatePrompt(), info);
    }

    public String bazi(int year, int month, int day, int hour, int minute, Integer gender, String name, int calendarType) {
        Map<String, String> params = new HashMap<>();
        params.put("name", name != null && !name.isBlank() ? name : "未知");
        params.put("sex", gender != null ? String.valueOf(gender) : "0");
        params.put("type", String.valueOf(calendarType));
        params.put("year", String.valueOf(year));
        params.put("month", String.valueOf(month));
        params.put("day", String.valueOf(day));
        params.put("hours", String.valueOf(hour));
        params.put("minute", String.valueOf(minute));
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) sb.append("姓名：").append(name).append("\n");
        JsonNode data = callYuanfenjuApi("/Bazi/paipan", params);
        if (data != null) {
            String result = formatYuanfenjuBazi(data);
            if (result != null) {
                sb.append(result);
                String wuxing = buildWuxingAnalysis(data.path("bazi_info").path("bazi"));
                if (wuxing != null) sb.append("\n\n").append(wuxing);
            } else {
                sb.append("[AI生成] ").append(callAI(buildBaziPrompt(), buildUserContent(year, month, day, hour, minute, gender, name)));
            }
        } else {
            sb.append("[AI生成] ").append(callAI(buildBaziPrompt(), buildUserContent(year, month, day, hour, minute, gender, name)));
        }
        sb.append("\n\n━━ 紫微斗数 ━━\n");
        sb.append(doZiwei(year, month, day, hour, minute, gender, name, calendarType));
        sb.append("\n\n---\n\n📌 数据由缘份居八字排盘 + 紫微排盘 API 提供");
        return sb.toString();
    }

    private String doZiwei(int year, int month, int day, int hour, int minute, Integer gender, String name, int calendarType) {
        Map<String, String> params = new HashMap<>();
        params.put("name", name != null && !name.isBlank() ? name : "未知");
        params.put("sex", gender != null ? String.valueOf(gender) : "0");
        params.put("type", String.valueOf(calendarType));
        params.put("year", String.valueOf(year));
        params.put("month", String.valueOf(month));
        params.put("day", String.valueOf(day));
        params.put("hours", String.valueOf(hour));
        params.put("minute", String.valueOf(minute));
        JsonNode data = callYuanfenjuApi("/Bazi/zwpan", params);
        if (data != null) {
            String result = formatYuanfenjuZiWei(data);
            if (result != null) return result;
        }
        return "[AI生成] " + callAI(buildZiWeiPrompt(), buildUserContent(year, month, day, hour, minute, gender, name));
    }

    public String faceReading(byte[] imageBytes, String prompt) {
        String imageUrl = uploadToImageHost(imageBytes);
        if (imageUrl != null) {
            String result = faceReadingViaYuanfenju(imageUrl);
            if (result != null) return result;
        }
        String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
        return callVisionAI(buildFaceReadingPrompt(), base64, prompt);
    }

    public String faceReading(String base64Image, String prompt) {
        return callVisionAI(buildFaceReadingPrompt(), base64Image, prompt);
    }

    private JsonNode callYuanfenjuApi(String path, Map<String, String> params) {
        String key = apiConfig.getYuanfenjuApiKey();
        if (key == null || key.isBlank()) return null;
        try {
            StringBuilder body = new StringBuilder();
            body.append("api_key=").append(URLEncoder.encode(key, "UTF-8"));
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    body.append("&").append(e.getKey()).append("=")
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                }
            }
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(YUANFENJU_BASE + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            var root = new ObjectMapper().readTree(hresp.body());
            int errcode = root.path("errcode").asInt(-1);
            if (errcode != 0) {
                log.warn("缘份居 {} API error: {}", path, root.path("errmsg").asText());
                return null;
            }
            return root.path("data");
        } catch (Exception e) {
            log.warn("缘份居 {} API 调用失败: {}", path, e.getMessage());
            return null;
        }
    }

    private String uploadToImageHost(byte[] imageBytes) {
        try {
            String boundary = "Boundary" + System.currentTimeMillis();
            var byteOut = new java.io.ByteArrayOutputStream();
            write(byteOut, ("--" + boundary + "\r\n").getBytes());
            write(byteOut, "Content-Disposition: form-data; name=\"smfile\"; filename=\"face.jpg\"\r\n".getBytes());
            write(byteOut, "Content-Type: image/jpeg\r\n\r\n".getBytes());
            write(byteOut, imageBytes);
            write(byteOut, ("\r\n--" + boundary + "--\r\n").getBytes());
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create("https://sm.ms/api/v2/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(byteOut.toByteArray()))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            var root = new ObjectMapper().readTree(hresp.body());
            if (root.path("success").asBoolean()) {
                return root.path("data").path("url").asText();
            }
            log.warn("sm.ms 上传失败: {}", hresp.body());
            return null;
        } catch (Exception e) {
            log.warn("图片上传失败: {}", e.getMessage());
            return null;
        }
    }

    private static void write(java.io.ByteArrayOutputStream out, byte[] data) {
        try { out.write(data); } catch (java.io.IOException ignored) {}
    }

    private String faceReadingViaYuanfenju(String imageUrl) {
        try {
            String key = apiConfig.getYuanfenjuApiKey();
            if (key == null || key.isBlank()) return null;
            String body = "api_key=" + URLEncoder.encode(key, "UTF-8")
                + "&image_url=" + URLEncoder.encode(imageUrl, "UTF-8");
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(YUANFENJU_BASE + "/Dashuju/mianxiang"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(20))
                    .build();
            HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
            var root = new ObjectMapper().readTree(hresp.body());
            if (root.path("errcode").asInt(-1) != 0) {
                log.warn("缘份居面相 API error: {}", root.path("errmsg").asText());
                return null;
            }
            return formatYuanfenjuFaceReading(root.path("data"));
        } catch (Exception e) {
            log.warn("缘份居面相 API 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private String formatYuanfenjuFaceReading(JsonNode data) {
        if (data == null || data.isMissingNode()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("━━ 面相分析（缘份居）━━\n");
            sb.append("年龄：").append(data.path("age").asText()).append("岁  ");
            sb.append("性别：").append(data.path("gender").asText().equals("male") ? "男" : "女").append("\n");
            sb.append("颜值：").append(data.path("beauty_desc").asText("")).append("\n");
            sb.append("脸型：").append(data.path("face_shape").asText("")).append("\n");
            sb.append(data.path("face_shape_desc").asText("")).append("\n\n");
            sb.append("眉型：").append(data.path("eyebrow_shape").asText("")).append("\n");
            sb.append(data.path("eyebrow_shape_desc").asText("")).append("\n\n");
            sb.append("眼型：").append(data.path("eye_shape").asText("")).append("\n");
            sb.append(data.path("eye_shape_desc").asText("")).append("\n\n");
            sb.append("鼻型：").append(data.path("nose_shape").asText("")).append("\n");
            sb.append(data.path("nose_shape_desc").asText("")).append("\n\n");
            sb.append("嘴型：").append(data.path("mouth_shape").asText("")).append("\n");
            sb.append(data.path("mouth_shape_desc").asText("")).append("\n\n");
            sb.append("面相五行：").append(data.path("mianxiang_five").asText(""))
                .append(" ").append(data.path("mianxiang_five_simple_desc").asText("")).append("\n");
            sb.append(data.path("mianxiang_five_detail_desc").asText("")).append("\n\n");
            sb.append("━━ 运势 ━━\n");
            sb.append("综合概述：").append(data.path("mianxiang_overview_desc").asText("")).append("\n\n");
            String s30 = data.path("mianxiang_further_30").asText("");
            String s40 = data.path("mianxiang_further_40").asText("");
            String s50 = data.path("mianxiang_further_50").asText("");
            String s60 = data.path("mianxiang_further_60").asText("");
            if (!s30.isEmpty()) sb.append("【0-30岁】").append(s30).append("\n\n");
            if (!s40.isEmpty()) sb.append("【31-40岁】").append(s40).append("\n\n");
            if (!s50.isEmpty()) sb.append("【41-50岁】").append(s50).append("\n\n");
            if (!s60.isEmpty()) sb.append("【51-60岁】").append(s60).append("\n\n");
            sb.append("—— 数据由缘份居面相 API 提供（麻衣相术 + 柳庄相法）");
            return sb.toString();
        } catch (Exception e) {
            log.warn("格式化缘份居面相结果失败", e);
            return null;
        }
    }

    private String formatYuanfenjuLunar(JsonNode data) {
        if (data == null || data.isMissingNode()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("━━ 农历转换 ━━\n");
        sb.append("公历：").append(data.path("yangli").asText("")).append("\n");
        sb.append("农历：").append(data.path("yinli").asText("")).append("\n");
        String shengxiao = getChineseZodiac(Integer.parseInt(data.path("yangli").asText("").substring(0, 4)));
        sb.append("生肖：").append(shengxiao).append("\n");
        sb.append("星期：").append(data.path("xingqi").asText("")).append("\n\n");

        JsonNode gz = data.path("ganzhi");
        if (!gz.isMissingNode()) {
            sb.append("━━ 干支纪年 ━━\n");
            sb.append("年柱：").append(gz.path("yeargan").asText("")).append(gz.path("yearzhi").asText("")).append("\n");
            sb.append("月柱：").append(gz.path("monthgan").asText("")).append(gz.path("monthzhi").asText("")).append("\n");
            sb.append("日柱：").append(gz.path("daygan").asText("")).append(gz.path("dayzhi").asText("")).append("\n\n");
        }

        String wuxing = data.path("wuxing").asText("");
        if (!wuxing.isEmpty()) sb.append("纳音：").append(wuxing).append("\n\n");

        String yi = data.path("yi").asText("");
        String ji = data.path("ji").asText("");
        String chongsha = data.path("chongsha").asText("");
        String jishen = data.path("jishen").asText("");
        String xiongshen = data.path("xiongshen").asText("");

        sb.append("━━ 黄历宜忌 ━━\n");
        sb.append("宜：").append(yi.isEmpty() ? "无" : yi).append("\n");
        sb.append("忌：").append(ji.isEmpty() ? "无" : ji).append("\n");
        sb.append("冲煞：").append(chongsha.isEmpty() ? "无" : chongsha).append("\n");
        if (!jishen.isEmpty()) sb.append("吉神：").append(jishen).append("\n");
        if (!xiongshen.isEmpty()) sb.append("凶神：").append(xiongshen).append("\n");

        String caishen = data.path("caishenfangwei").asText("");
        String xishen = data.path("xishenfangwei").asText("");
        if (!caishen.isEmpty()) sb.append("财神方位：").append(caishen).append("\n");
        if (!xishen.isEmpty()) sb.append("喜神方位：").append(xishen).append("\n");

        String zhixing = data.path("zhixing").asText("");
        String liuyao = data.path("liuyao").asText("");
        if (!zhixing.isEmpty()) sb.append("值星：").append(zhixing).append("  六曜：").append(liuyao).append("\n");

        return sb.toString();
    }

    private String formatYuanfenjuBazi(JsonNode data) {
        if (data == null || data.isMissingNode()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            JsonNode base = data.path("base_info");
            sb.append("━━ 四柱八字 ━━\n");
            sb.append("出生时间：").append(base.path("gongli").asText("")).append("\n");
            sb.append("农历：").append(base.path("nongli").asText("")).append("\n");

            JsonNode baziInfo = data.path("bazi_info");
            JsonNode bazi = baziInfo.path("bazi");
            JsonNode tgGod = baziInfo.path("tg_cg_god");
            JsonNode nayin = baziInfo.path("na_yin");
            String[] pillars = {"年柱", "月柱", "日柱", "时柱"};
            for (int i = 0; i < 4 && i < bazi.size(); i++) {
                sb.append(pillars[i]).append("：").append(bazi.get(i).asText(""));
                if (i < nayin.size() && !nayin.get(i).asText("").isEmpty())
                    sb.append("（").append(nayin.get(i).asText("")).append("）");
                if (i < tgGod.size() && !tgGod.get(i).asText("").isEmpty())
                    sb.append(" ").append(tgGod.get(i).asText(""));
                sb.append("\n");
            }

            sb.append("\n━━ 起运 ━━\n");
            sb.append("起运：").append(base.path("qiyun").asText("")).append("\n");
            sb.append("交运：").append(base.path("jiaoyun").asText("")).append("\n");
            String zhengge = base.path("zhengge").asText("");
            if (!zhengge.isEmpty()) sb.append("正格：").append(zhengge).append("\n");

            JsonNode dayun = data.path("dayun_info");
            JsonNode big = dayun.path("big");
            JsonNode bigGod = dayun.path("big_god");
            JsonNode xuSui = dayun.path("xu_sui");
            JsonNode startYear = dayun.path("big_start_year");
            JsonNode endYear = dayun.path("big_end_year");
            if (big.isArray() && big.size() > 0) {
                sb.append("\n━━ 大运 ━━\n");
                for (int i = 0; i < Math.min(8, big.size()); i++) {
                    sb.append(xuSui.get(i).asText("")).append("岁 ")
                        .append(big.get(i).asText(""));
                    if (i < bigGod.size() && !bigGod.get(i).asText("").isEmpty())
                        sb.append(" ").append(bigGod.get(i).asText(""));
                    if (i < startYear.size() && i < endYear.size())
                        sb.append("  ").append(startYear.get(i).asText(""))
                            .append("-").append(endYear.get(i).asText(""));
                    sb.append("\n");
                }
            }

            JsonNode detail = data.path("detail_info");
            if (!detail.isMissingNode()) {
                JsonNode zhuxing = detail.path("zhuxing");
                if (!zhuxing.isMissingNode()) {
                    sb.append("\n━━ 主星 ━━\n");
                    sb.append("年柱：").append(zhuxing.path("year").asText("")).append("\n");
                    sb.append("月柱：").append(zhuxing.path("month").asText("")).append("\n");
                    sb.append("日主：").append(zhuxing.path("day").asText("")).append("\n");
                    sb.append("时柱：").append(zhuxing.path("hour").asText("")).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("格式化缘份居八字结果失败", e);
            return null;
        }
    }

    private String buildWuxingAnalysis(JsonNode baziArray) {
        if (baziArray == null || !baziArray.isArray() || baziArray.size() < 4) return null;
        try {
            java.util.Map<String, Integer> count = new java.util.HashMap<>();
            count.put("金", 0); count.put("木", 0); count.put("水", 0); count.put("火", 0); count.put("土", 0);
            String dayMaster = "";
            for (int i = 0; i < baziArray.size(); i++) {
                String pillar = baziArray.get(i).asText("");
                if (pillar.length() < 2) continue;
                String gan = pillar.substring(0, 1);
                String zhi = pillar.substring(1);
                int gi = GAN.indexOf(gan);
                if (gi >= 0) {
                    count.merge(GAN_WUXING[gi], 1, Integer::sum);
                    if (i == 2) dayMaster = GAN_WUXING[gi];
                }
                for (int j = 0; j < zhi.length(); j++) {
                    int zi = ZHI.indexOf(zhi.charAt(j));
                    if (zi >= 0) count.merge(ZHI_WUXING[zi], 1, Integer::sum);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("━━ 五行统计 ━━\n");
            String maxEle = "", minEle = "";
            int max = 0, min = Integer.MAX_VALUE;
            for (java.util.Map.Entry<String, Integer> e : count.entrySet()) {
                sb.append(e.getKey()).append("：").append(e.getValue()).append("  ");
                if (e.getValue() > max) { max = e.getValue(); maxEle = e.getKey(); }
                if (e.getValue() < min) { min = e.getValue(); minEle = e.getKey(); }
            }
            sb.append("\n日主：").append(dayMaster.isEmpty() ? "未知" : dayMaster);
            if (!maxEle.isEmpty()) sb.append("，").append(maxEle).append("最旺");
            if (!minEle.isEmpty()) sb.append("，").append(minEle).append("最弱");
            return sb.toString();
        } catch (Exception e) {
            log.warn("五行分析失败", e);
            return null;
        }
    }

    private String formatYuanfenjuZiWei(JsonNode data) {
        if (data == null || data.isMissingNode()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            JsonNode base = data.path("base_info");
            sb.append("━━ 紫微斗数 ━━\n");
            sb.append("出生：").append(base.path("gongli").asText("")).append("\n");
            String sex = base.path("sex").asText("");
            String gendertype = base.path("gendertype").asText("");
            if (!sex.isEmpty() || !gendertype.isEmpty()) {
                sb.append("性别：").append(sex.isEmpty() ? gendertype : sex);
                if (!gendertype.isEmpty() && !gendertype.equals(sex))
                    sb.append("（").append(gendertype).append("）");
                sb.append("\n");
            }
            sb.append("命宫/身宫：").append(base.path("minggong").asText(""))
                .append("/").append(base.path("shengong").asText("")).append("\n");
            sb.append("命主：").append(base.path("mingzhu").asText(""))
                .append("  身主：").append(base.path("shenzhu").asText("")).append("\n");
            sb.append(base.path("mingju").asText("")).append("\n\n");

            JsonNode gongPan = data.path("gong_pan");
            if (!gongPan.isArray() || gongPan.size() == 0) {
                sb.append("十二宫数据不可用");
                return sb.toString();
            }

            sb.append("━━ 十四主星 ━━\n");
            java.util.Map<String, String[]> mainStars = new java.util.LinkedHashMap<>();
            for (JsonNode palace : gongPan) {
                String pName = palace.path("minggong").asText("");
                if (pName.isEmpty()) continue;
                addMainStar(mainStars, palace, "ziweixing", pName);
                addMainStar(mainStars, palace, "tianfuxing", pName);
            }
            int col = 0;
            for (java.util.Map.Entry<String, String[]> e : mainStars.entrySet()) {
                sb.append(String.format("%-10s", e.getKey() + "：" + e.getValue()[0] + "[" + e.getValue()[1] + "]"));
                if (!e.getValue()[2].isEmpty()) sb.append("(").append(e.getValue()[2]).append(")");
                col++;
                if (col % 2 == 0) sb.append("\n"); else sb.append("  ");
            }
            if (col % 2 != 0) sb.append("\n");
            sb.append("\n");

            sb.append("━━ 十二宫 ━━\n");
            for (JsonNode palace : gongPan) {
                String pName = palace.path("minggong").asText("");
                if (pName.isEmpty()) continue;
                String daxian = palace.path("daxian").asText("");
                String xiaoxian = palace.path("xiaoxian").asText("");
                sb.append("【").append(pName).append("】");
                if (!daxian.isEmpty()) sb.append(" ").append(daxian);
                sb.append("\n");
                String mainStarsStr = buildMainStarInPalace(palace);
                if (!mainStarsStr.isEmpty()) sb.append("  ").append(mainStarsStr).append("\n");
                String auxStars = buildAuxStarString(palace);
                if (!auxStars.isEmpty()) sb.append("  辅星：").append(auxStars).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("格式化缘份居紫微结果失败", e);
            return null;
        }
    }

    private void addMainStar(java.util.Map<String, String[]> map, JsonNode palace, String starField, String pName) {
        String star = palace.path(starField).asText("");
        if (star.isEmpty()) return;
        String yao = palace.path(starField + "_xingyao").asText("");
        String sihua = palace.path(starField + "_sihua").asText("");
        map.put(star, new String[]{pName, yao.isEmpty() ? "--" : yao, sihua});
    }

    private String buildMainStarInPalace(JsonNode palace) {
        StringBuilder sb = new StringBuilder();
        String[] fields = {"ziweixing", "tianfuxing"};
        for (String f : fields) {
            String star = palace.path(f).asText("");
            if (star.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n  ");
            sb.append("★ ").append(star);
            String yao = palace.path(f + "_xingyao").asText("");
            if (!yao.isEmpty()) sb.append("[").append(yao).append("]");
            String sihua = palace.path(f + "_sihua").asText("");
            if (!sihua.isEmpty()) sb.append("(").append(sihua).append(")");
        }
        return sb.toString();
    }

    private String buildAuxStarString(JsonNode palace) {
        StringBuilder sb = new StringBuilder();
        String[][] groups = {
            {"monthxing", "monthxing_xingyao", "monthxing_sihua"},
            {"hourxing", "hourxing_xingyao", "hourxing_sihua"},
            {"yearganxing", "yearganxing_xingyao", "yearganxing_sihua"},
            {"yearzhixing", "yearzhixing_xingyao", "yearzhixing_sihua"},
            {"qitaxing", "qitaxing_xingyao", "qitaxing_sihua"},
        };
        for (String[] g : groups) {
            String star = palace.path(g[0]).asText("");
            if (star.isEmpty()) continue;
            if (sb.length() > 0) sb.append("  ");
            sb.append(star);
            String yao = palace.path(g[1]).asText("");
            if (!yao.isEmpty()) sb.append("[").append(yao).append("]");
            String sihua = palace.path(g[2]).asText("");
            if (!sihua.isEmpty()) sb.append("(").append(sihua).append(")");
        }
        return sb.toString();
    }

    private String formatYuanfenjuIChing(JsonNode data) {
        if (data == null || data.isMissingNode()) return null;
        try {
            JsonNode guaInfo = data.path("gua_info");
            if (guaInfo.isMissingNode()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("━━ 六爻占卜 ━━\n");

            JsonNode bengua = guaInfo.path("bengua");
            if (!bengua.isMissingNode()) {
                sb.append("本卦：").append(bengua.path("gua_name").asText(""))
                    .append("（").append(bengua.path("gua_gong").asText("")).append("）")
                    .append(" ").append(bengua.path("gua_xiongji").asText("")).append("\n");
                String qian = bengua.path("gua_qian").asText("");
                if (!qian.isEmpty()) sb.append("卦签：").append(qian).append("\n");
                String desc = bengua.path("gua_qian_desc").asText("");
                if (!desc.isEmpty()) sb.append("解卦：").append(desc).append("\n");

                JsonNode guaDesc = bengua.path("gua_description");
                if (!guaDesc.isMissingNode()) {
                    sb.append("\n┏ 占事结果 ┓\n");
                    addDesc(sb, guaDesc, "事业", "gua_shiye");
                    addDesc(sb, guaDesc, "经商", "gua_jingshang");
                    addDesc(sb, guaDesc, "求名", "gua_qiuming");
                    addDesc(sb, guaDesc, "外出", "gua_waichu");
                    addDesc(sb, guaDesc, "婚恋", "gua_hunlian");
                    addDesc(sb, guaDesc, "决策", "gua_juece");
                }
            }

            JsonNode biangua = guaInfo.path("biangua");
            if (!biangua.isMissingNode() && !biangua.path("gua_name").asText("").isEmpty()) {
                sb.append("\n变卦：").append(biangua.path("gua_name").asText(""))
                    .append("（").append(biangua.path("gua_gong").asText("")).append("）")
                    .append(" ").append(biangua.path("gua_xiongji").asText("")).append("\n");
                String bDesc = biangua.path("gua_qian_desc").asText("");
                if (!bDesc.isEmpty()) sb.append("解卦：").append(bDesc).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("格式化缘份居六爻结果失败", e);
            return null;
        }
    }

    private void addDesc(StringBuilder sb, JsonNode desc, String label, String field) {
        String text = desc.path(field).asText("");
        if (!text.isEmpty()) sb.append("【").append(label).append("】").append(text).append("\n");
    }

    private String formatYuanfenjuAuspiciousDate(JsonNode data, String eventType) {
        if (data == null || data.isMissingNode()) return null;
        try {
            JsonNode baseInfo = data.path("base_info");
            JsonNode detailInfo = data.path("detail_info");
            StringBuilder sb = new StringBuilder();

            if (!baseInfo.isMissingNode()) {
                sb.append("吉日区间：").append(baseInfo.path("summarize").asText("")).append("\n\n");
            }

            if (detailInfo.isArray()) {
                int count = 0;
                for (JsonNode day : detailInfo) {
                    if (count >= 5) break;
                    sb.append("### ").append(day.path("yangli").asText(""))
                        .append(" ").append(day.path("xingqi").asText("")).append("\n");
                    sb.append("农历：").append(day.path("yinli").asText("")).append("\n");
                    String yi = day.path("yi").asText("");
                    String ji = day.path("ji").asText("");
                    if (!yi.isEmpty()) sb.append("宜：").append(yi).append("\n");
                    if (!ji.isEmpty()) sb.append("忌：").append(ji).append("\n");
                    sb.append("冲煞：").append(day.path("chongsha").asText("")).append("\n");
                    sb.append("\n");
                    count++;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("格式化缘份居择吉结果失败", e);
            return null;
        }
    }

    private int mapEventTypeToIncident(String eventType) {
        if (eventType == null) return 0;
        switch (eventType) {
            case "结婚": case "嫁娶": case "领证": return 4;
            case "搬家": return 0;
            case "开业": case "开张": case "开市": return 7;
            case "出行": return 11;
            case "入宅": return 2;
            case "订婚": case "纳采": return 3;
            case "动土": return 10;
            case "装修": case "修造": return 1;
            default: return 0;
        }
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

    private String buildBaziPrompt() {
        return """
你是精通中国传统命理学的资深命理师，擅长子平八字（四柱命理）。

请根据用户提供的出生信息，按以下步骤推算：

━━ 第一步：排八字（四柱） ━━
1. 公历→农历转换
2. 计算年柱（年干+年支，立春分界）
3. 计算月柱（月干+月支，节气分界）
4. 计算日柱（日干+日支）
5. 计算时柱（时干+时支）
输出格式：X年 月柱 日柱 时柱，每个天干地支标注五行

━━ 第二步：十神分析 ━━
- 日主（日干）是什么
- 年柱、月柱、时柱的十神关系

━━ 第三步：五行分析 ━━
- 统计八字中金木水火土的数量
- 判断日主强弱（身强/身弱）
- 喜用神和忌神

━━ 第四步：大运分析 ━━
- 起运年龄和时间
- 排大运（每10年一运）

━━ 第五步：命局总评 ━━
- 性格特质
- 事业财运
- 感情婚姻
- 健康提示
- 当前流年运势

格式要求：专业术语后加括号解释，每条分析不超过3句话，不迷信。""";
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

    // ==================== 星座 ====================

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
        "今天适合主动出击，抓住机会展现自己。",
        "保持耐心，好事多磨，下午会有转机。",
        "适合与人沟通交流，可能会有意外收获。",
        "今天要注意细节，小失误可能带来大麻烦。",
        "运势平稳，适合处理积压已久的任务。",
        "财运不错，但投资仍需谨慎。",
        "感情方面有惊喜，单身者有望脱单。",
        "工作压力较大，注意劳逸结合。",
        "今天适合学习新知识，提升自己。",
        "人际关系运佳，适合社交活动。",
        "健康方面需注意，不要熬夜。",
        "今天适合做决定，直觉准确。",
        "财运上升，但避免冲动消费。",
        "事业上会遇到贵人，把握机会。",
        "今天适合独处，整理思绪。",
        "好运降临，之前努力的事情会有结果。"
    };

    public String queryConstellationByDate(int month, int day) {
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
                log.info("星座查询API响应: {}", hresp.body());
                JsonNode qRoot = new ObjectMapper().readTree(hresp.body());
                if (qRoot.path("error_code").asInt() == 0) {
                    JsonNode qResult = qRoot.path("result");
                    String name = qResult.path("name").asText("");
                    if (!name.isBlank()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(month).append("月").append(day).append("日出生的人是 **").append(name).append("**\n\n");
                        if (qResult.has("date")) {
                            sb.append("日期范围: ").append(qResult.path("date").asText()).append("\n");
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
        if (zodiac == null) return "无效的日期: " + month + "月" + day + "日";
        return month + "月" + day + "日出生的人是 **" + zodiac + "**";
    }

    public String queryConstellationHoroscope(String sign) {
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
                log.info("星座API响应: {}", hresp.body());
                JsonNode root = new ObjectMapper().readTree(hresp.body());
                if (root.path("error_code").asInt() == 0) {
                    return formatHoroscopeApiResult(root);
                }
                log.warn("星座API返回错误: {}", root.path("reason").asText());
            } catch (Exception e) {
                log.warn("星座API调用失败，降级到本地生成", e);
            }
        }
        return generateLocalHoroscope(sign);
    }

    private String formatHoroscopeApiResult(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(root.path("name").asText()).append(" ")
            .append(root.path("datetime").asText()).append("运势**\n\n");
        if (root.has("summary")) {
            sb.append(root.path("summary").asText()).append("\n\n");
        }
        sb.append("综合运势: ").append(root.path("all").asText()).append("\n");
        sb.append("爱情运势: ").append(root.path("love").asText()).append("\n");
        sb.append("事业运势: ").append(root.path("work").asText()).append("\n");
        sb.append("财运运势: ").append(root.path("money").asText()).append("\n");
        sb.append("健康运势: ").append(root.path("health").asText()).append("\n\n");
        sb.append("幸运数字: ").append(root.path("number").asText()).append("\n");
        sb.append("幸运色: ").append(root.path("color").asText()).append("\n");
        sb.append("速配星座: ").append(root.path("QFriend").asText());
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
        sb.append("**").append(sign).append(" ").append(today).append(" 运势**\n\n");
        sb.append(advice).append("\n\n");
        sb.append("综合运势: ").append(scoreToStars(allScore)).append("\n");
        sb.append("爱情运势: ").append(scoreToStars(loveScore)).append("\n");
        sb.append("事业运势: ").append(scoreToStars(workScore)).append("\n");
        sb.append("财运运势: ").append(scoreToStars(moneyScore)).append("\n");
        sb.append("健康运势: ").append(scoreToStars(healthScore)).append("\n\n");
        sb.append("幸运数字: ").append(luckyNum).append("\n");
        sb.append("幸运色: ").append(luckyColor).append("\n");
        sb.append("速配星座: ").append(compatibleSign);
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
