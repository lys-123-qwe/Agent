package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundService {

    private static final Logger log = LoggerFactory.getLogger(FundService.class);

    private static final String SINA_API = "http://hq.sinajs.cn/list=f_";
    private static final String EASTMONEY_LSJZ_API = "https://api.fund.eastmoney.com/f10/lsjz";
    private static final String EASTMONEY_MANAGER_URL = "https://fundf10.eastmoney.com/jjjl_";

    private static final Map<String, String> FUND_MAP;

    static {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("易方达蓝筹精选", "005827");
        map.put("招商中证白酒", "161725");
        map.put("中欧医疗健康", "003095");
        map.put("诺安成长混合", "320007");
        map.put("富国天惠成长", "161005");
        map.put("兴全合润混合", "163406");
        map.put("景顺长城新兴成长", "260108");
        map.put("天弘沪深300", "000961");
        map.put("广发稳健增长", "270002");
        map.put("易方达消费行业", "110022");
        map.put("中概互联", "164906");
        FUND_MAP = java.util.Collections.unmodifiableMap(map);
    }

    private final RestClient restClient;

    public FundService() {
        this.restClient = RestClient.create();
    }

    public String query(String input) {
        if (input == null || input.isBlank()) {
            return "请提供基金名称或代码，如：易方达蓝筹精选、005827";
        }

        String code = resolveCode(input.trim());
        if (code == null) return "不支持的基金: " + input;

        return queryDetail(code);
    }

    private String resolveCode(String input) {
        if (input.matches("^\\d{6}$")) return input;
        String mapped = FUND_MAP.get(input);
        if (mapped != null) return mapped;
        for (var entry : FUND_MAP.entrySet()) {
            if (entry.getKey().contains(input) || input.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String queryDetail(String code) {
        String basic = queryFromSina(code);
        if (basic == null) return null;

        String history = queryHistory(code);
        String manager = queryManager(code);

        StringBuilder sb = new StringBuilder(basic);
        if (history != null) {
            sb.append("\n\n").append(history);
        }
        if (manager != null) {
            sb.append("\n\n").append(manager);
        }
        sb.append("\n\n详情: https://fund.eastmoney.com/").append(code).append(".html");
        return sb.toString();
    }

    private String queryFromSina(String code) {
        try {
            String url = SINA_API + code;
            byte[] respBytes = restClient.get()
                .uri(url)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve()
                .body(byte[].class);
            Charset gbk = Charset.forName("GBK");
            String text = new String(respBytes, gbk);
            return parseSinaResponse(text, code);
        } catch (Exception e) {
            log.warn("新浪基金接口失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseSinaResponse(String text, String code) {
        try {
            if (text == null || text.isBlank()) return null;
            int eq = text.indexOf('"');
            if (eq < 0) return null;
            String data = text.substring(eq + 1);
            int end = data.lastIndexOf('"');
            if (end > 0) data = data.substring(0, end);
            if (data.isBlank()) return null;

            String[] parts = data.split(",");
            if (parts.length < 5) return null;

            String name = parts[0];
            String dwjz = parts[1];
            String ljjz = parts[2];
            String prevJz = parts[3];
            String date = parts[4];

            double cur = toDouble(dwjz);
            double prev = toDouble(prevJz);
            double change = cur - prev;
            double changePct = prev > 0 ? change / prev * 100 : 0;

            StringBuilder sb = new StringBuilder();
            sb.append(name).append("（").append(code).append("）\n");
            sb.append("净值: ").append(dwjz);
            if (!ljjz.isBlank() && !ljjz.equals(dwjz)) {
                sb.append("（累计: ").append(ljjz).append("）");
            }
            sb.append("\n涨跌幅: ").append(String.format("%+.2f", changePct)).append("%\n");
            sb.append("日期: ").append(date);
            return sb.toString();
        } catch (Exception e) {
            log.warn("解析新浪基金数据失败", e);
            return null;
        }
    }

    private String queryHistory(String code) {
        try {
            String url = EASTMONEY_LSJZ_API + "?callback=jQuery&fundCode=" + code + "&pageIndex=1&pageSize=5";
            byte[] respBytes = restClient.get()
                .uri(url)
                .header("Referer", "https://fund.eastmoney.com")
                .retrieve()
                .body(byte[].class);

            String text = new String(respBytes, java.nio.charset.StandardCharsets.UTF_8);
            if (text == null || text.isBlank() || text.contains("\"Data\":null")) return null;

            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end < 0) return null;

            String json = text.substring(start, end + 1);
            var root = new tools.jackson.databind.ObjectMapper().readTree(json);
            var data = root.path("Data");
            if (data.isNull() || data.isMissingNode()) return null;

            var lsjzList = data.path("LSJZList");
            if (!lsjzList.isArray() || lsjzList.isEmpty()) return null;

            StringBuilder sb = new StringBuilder("近期净值:");
            int count = Math.min(lsjzList.size(), 5);
            for (int i = 0; i < count; i++) {
                var item = lsjzList.get(i);
                String d = item.path("FSRQ").asText();
                String v = item.path("DWJZ").asText();
                String chg = item.path("JZZZL").asText();
                sb.append("\n  ").append(d).append("  ").append(v);
                if (!chg.isBlank()) {
                    sb.append("  ").append(String.format("%+.2f", toDouble(chg))).append("%");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("历史净值查询失败: {}", e.getMessage());
            return null;
        }
    }

    private String queryManager(String code) {
        try {
            String url = EASTMONEY_MANAGER_URL + code + ".html";
            byte[] respBytes = restClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .retrieve()
                .body(byte[].class);

            Charset gbk = Charset.forName("GBK");
            String html = new String(respBytes, gbk);

            List<String> managerNames = new ArrayList<>();
            Pattern namePattern = Pattern.compile(
                "<a\\s+href=\"//fund\\.eastmoney\\.com/manager/\\d+\\.html\"\\s*>([^<]+)</a>");
            Matcher nameMatcher = namePattern.matcher(html);
            while (nameMatcher.find()) {
                String name = nameMatcher.group(1).trim();
                if (!managerNames.contains(name)) {
                    managerNames.add(name);
                }
            }

            if (managerNames.isEmpty()) return null;

            StringBuilder sb = new StringBuilder("基金经理: ");
            sb.append(String.join("、", managerNames));

            Pattern datePattern = Pattern.compile(
                "上任日期[：:][\\s]*</strong>([\\d-]+)");
            Matcher dateMatcher = datePattern.matcher(html);
            if (dateMatcher.find()) {
                sb.append("\n上任日期: ").append(dateMatcher.group(1));
            }

            if (!managerNames.isEmpty()) {
                String mName = managerNames.get(0);
                Pattern bioPattern = Pattern.compile(
                    Pattern.quote(mName) + "[：:]*\\s*([^<。]+。[^<。]*)");
                Matcher bioMatcher = bioPattern.matcher(html);
                if (bioMatcher.find()) {
                    String bio = bioMatcher.group(1).trim();
                    if (bio.length() > 10) {
                        sb.append("\n简介: ").append(bio);
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("基金经理信息查询失败: {}", e.getMessage());
            return null;
        }
    }

    private double toDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

}
