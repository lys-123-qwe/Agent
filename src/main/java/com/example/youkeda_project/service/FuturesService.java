package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class FuturesService {

    private static final Logger log = LoggerFactory.getLogger(FuturesService.class);

    private static final String SINA_API = "http://hq.sinajs.cn/list=";
    private static final String TENCENT_API = "http://qt.gtimg.cn/q=";

    private static final Map<String, String> FUTURE_CODE_MAP;

    static {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        // 股指期货
        map.put("IF", "IF888"); map.put("沪深300期货", "IF888");
        map.put("IH", "IH888"); map.put("上证50期货", "IH888");
        map.put("IC", "IC888"); map.put("中证500期货", "IC888");
        map.put("IM", "IM888"); map.put("中证1000期货", "IM888");
        // 贵金属
        map.put("AU", "AU888"); map.put("沪金", "AU888");
        map.put("AG", "AG888"); map.put("沪银", "AG888");
        // 有色金属
        map.put("CU", "CU888"); map.put("沪铜", "CU888");
        map.put("AL", "AL888"); map.put("沪铝", "AL888");
        map.put("ZN", "ZN888"); map.put("沪锌", "ZN888");
        map.put("NI", "NI888"); map.put("沪镍", "NI888");
        map.put("PB", "PB888"); map.put("沪铅", "PB888");
        map.put("SN", "SN888"); map.put("沪锡", "SN888");
        // 黑色系
        map.put("RB", "RB888"); map.put("螺纹钢", "RB888");
        map.put("HC", "HC888"); map.put("热卷", "HC888");
        map.put("I", "I888"); map.put("铁矿石", "I888");
        map.put("JM", "JM888"); map.put("焦煤", "JM888");
        map.put("J", "J888"); map.put("焦炭", "J888");
        // 能源化工
        map.put("SC", "SC888"); map.put("原油", "SC888");
        map.put("MA", "MA888"); map.put("甲醇", "MA888");
        map.put("TA", "TA888"); map.put("PTA", "TA888");
        map.put("V", "V888"); map.put("PVC", "V888");
        map.put("PP", "PP888"); map.put("聚丙烯", "PP888");
        map.put("L", "L888"); map.put("聚乙烯", "L888");
        map.put("BU", "BU888"); map.put("沥青", "BU888");
        map.put("FU", "FU888"); map.put("燃油", "FU888");
        map.put("EG", "EG888"); map.put("乙二醇", "EG888");
        // 农产品
        map.put("C", "C888"); map.put("玉米", "C888");
        map.put("A", "A888"); map.put("豆一", "A888");
        map.put("M", "M888"); map.put("豆粕", "M888");
        map.put("Y", "Y888"); map.put("豆油", "Y888");
        map.put("P", "P888"); map.put("棕榈油", "P888");
        map.put("SR", "SR888"); map.put("白糖", "SR888");
        map.put("CF", "CF888"); map.put("棉花", "CF888");
        map.put("OI", "OI888"); map.put("菜油", "OI888");
        map.put("RM", "RM888"); map.put("菜粕", "RM888");
        map.put("JD", "JD888"); map.put("鸡蛋", "JD888");
        // 股指期权
        map.put("IO", "IO888"); map.put("沪深300期权", "IO888");
        map.put("MO", "MO888"); map.put("中证1000期权", "MO888");
        FUTURE_CODE_MAP = java.util.Collections.unmodifiableMap(map);
    }

    private final RestClient restClient;

    public FuturesService() {
        this.restClient = RestClient.create();
    }

    public String query(String input) {
        if (input == null || input.isBlank()) {
            return "请提供期货品种名称或代码，如：沪金、螺纹钢、原油、IF、RB888";
        }

        String code = resolveCode(input.trim().toUpperCase());
        if (code == null) {
            return "不支持的期货品种: " + input + "\n支持的品种：沪金、沪银、螺纹钢、铁矿石、沪铜、原油等，或输入合约代码如 RB888";
        }
        // 去掉 "888" 后缀，新浪接口主力合约用 "888"
        return queryFromSina(code);
    }

    private String resolveCode(String input) {
        if (input.endsWith("888")) {
            return input;
        }
        String mapped = FUTURE_CODE_MAP.get(input);
        if (mapped != null) return mapped;
        // 尝试直接作为代码查询
        if (input.matches("^[A-Z]{1,2}\\d{2,4}$")) {
            return input;
        }
        return null;
    }

    private String queryFromSina(String code) {
        // 尝试多种代码格式：AU888 → AU0 → 直接查腾讯
        String[] codesToTry = {code, code.replace("888", "0"), code.replace("888", "")};
        for (String tryCode : codesToTry) {
            try {
                String url = SINA_API + "nf_" + tryCode;
                log.info("新浪期货请求: nf_{}", tryCode);

                byte[] respBytes = restClient.get()
                    .uri(url)
                    .header("Referer", "https://finance.sina.com.cn")
                    .retrieve()
                    .body(byte[].class);

                String text = new String(respBytes, StandardCharsets.UTF_8);
                log.info("新浪期货响应: {}", text);

                String parsed = parseSinaResponse(text, tryCode);
                if (parsed != null) return parsed;

            } catch (Exception e) {
                log.warn("新浪期货请求失败: {}", tryCode, e);
            }
        }

        // 新浪失败 → 腾讯兜底
        return queryFromTencent(code);
    }

    private String queryFromTencent(String code) {
        String[] codesToTry = {code, code.replace("888", ""), code.replace("888", "0")};
        for (String tryCode : codesToTry) {
            try {
                String url = TENCENT_API + "f_" + tryCode;
                log.info("腾讯期货请求: f_{}", tryCode);

                byte[] respBytes = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(byte[].class);

                String text = new String(respBytes, StandardCharsets.UTF_8);
                log.info("腾讯期货响应: {}", text);

                String parsed = parseTencentResponse(text, tryCode);
                if (parsed != null) return parsed;

            } catch (Exception e) {
                log.warn("腾讯期货请求失败: {}", tryCode, e);
            }
        }
        return null;
    }

    private String parseSinaResponse(String text, String code) {
        try {
            if (text == null || text.isBlank()) return null;
            // 新浪返回格式: var hq_str_nf_RB888="螺纹钢2305,3835,3818,3845,3832,3837,..."
            int eq = text.indexOf('"');
            if (eq < 0) return null;
            String data = text.substring(eq + 1);
            int end = data.lastIndexOf('"');
            if (end > 0) data = data.substring(0, end);
            if (data.isBlank()) return null; // 空数据

            String[] parts = data.split(",");
            if (parts.length < 15) return null;

            log.debug("新浪期货原始数据: {}", data);

            String name = parts[0];
            String open = parts[1];
            String lastClose = parts[2];
            String current = parts[3];
            String high = parts[4];
            String low = parts.length > 5 ? parts[5] : "";
            if (low.isEmpty() && parts.length > 6) low = parts[6]; // 兼容不同索引
            String volume = parts.length > 8 ? parts[8] : "";
            String hold = parts.length > 9 ? parts[9] : "";

            double cur = toDouble(current);
            double preClose = toDouble(lastClose);
            double change = cur - preClose;
            double changePct = preClose > 0 ? change / preClose * 100 : 0;

            String nameCn = getChineseName(code);
            String lowDisp = (low == null || low.isEmpty()) ? "-" : low;
            String volDisp = (volume == null || volume.isEmpty()) ? "-" : volume;
            String holdDisp = (hold == null || hold.isEmpty()) ? "-" : hold;

            return String.format(
                "%s %s\n当前价: %s | 涨跌: %+.0f (%+.2f%%)\n开: %s 高: %s 低: %s 昨收: %s\n成交量: %s 手 | 持仓量: %s 手",
                nameCn, name, current, change, changePct, open, high, lowDisp, lastClose, volDisp, holdDisp
            );

        } catch (Exception e) {
            log.warn("解析新浪期货数据失败", e);
            return null;
        }
    }

    private String parseTencentResponse(String text, String code) {
        try {
            if (text == null || text.isBlank()) return null;
            int eq = text.indexOf('"');
            if (eq < 0) return null;
            String data = text.substring(eq + 1);
            int end = data.lastIndexOf('"');
            if (end > 0) data = data.substring(0, end);
            if (data.isBlank() || "1".equals(data)) return null; // 空数据或无匹配

            String[] parts = data.split("~");
            if (parts.length < 40) return null;

            String name = parts[1];
            String current = parts[3];
            String lastClose = parts[4];
            String open = parts[5];
            String high = parts[6];
            String low = parts[7];
            String volume = parts[10];
            String hold = parts[11];

            double cur = toDouble(current);
            double preClose = toDouble(lastClose);
            double change = cur - preClose;
            double changePct = preClose > 0 ? change / preClose * 100 : 0;

            String nameCn = getChineseName(code);
            String lowDisp = (low == null || low.isEmpty()) ? "-" : low;
            String volDisp = (volume == null || volume.isEmpty()) ? "-" : volume;
            String holdDisp = (hold == null || hold.isEmpty()) ? "-" : hold;

            return String.format(
                "%s %s\n当前价: %s | 涨跌: %+.0f (%+.2f%%)\n开: %s 高: %s 低: %s 昨收: %s\n成交量: %s 手 | 持仓量: %s 手",
                nameCn, name, current, change, changePct, open, high, lowDisp, lastClose, volDisp, holdDisp
            );

        } catch (Exception e) {
            log.warn("解析腾讯期货数据失败", e);
            return null;
        }
    }

    private String getChineseName(String code) {
        String base = code.replaceAll("888$", "");
        for (var entry : FUTURE_CODE_MAP.entrySet()) {
            if (entry.getValue().equals(code) && entry.getKey().length() > 2) {
                return entry.getKey();
            }
        }
        return base;
    }

    private double toDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
