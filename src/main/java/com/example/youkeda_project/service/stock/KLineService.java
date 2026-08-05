package com.example.youkeda_project.service.stock;

import com.example.youkeda_project.util.StockCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class KLineService {

    private static final Logger log = LoggerFactory.getLogger(KLineService.class);

    private static final String SINA_KLINE_API =
        "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData";
    private static final String SINA_FUTURES_KLINE_API =
        "http://stock.finance.sina.com.cn/futures/api/json_v2.php/IndexService.getInnerFuturesDailyKLine";

    private static final Map<String, String> FUTURES_MAP;
    private static final Set<String> FUTURES_BASES;

    static {
        Map<String, String> fmap = new LinkedHashMap<>();
        fmap.put("沪金", "AU"); fmap.put("沪银", "AG");
        fmap.put("沪铜", "CU"); fmap.put("沪铝", "AL");
        fmap.put("沪锌", "ZN"); fmap.put("沪镍", "NI");
        fmap.put("螺纹钢", "RB"); fmap.put("热卷", "HC");
        fmap.put("铁矿石", "I"); fmap.put("焦煤", "JM"); fmap.put("焦炭", "J");
        fmap.put("原油", "SC"); fmap.put("甲醇", "MA");
        fmap.put("PTA", "TA"); fmap.put("PVC", "V");
        fmap.put("聚丙烯", "PP"); fmap.put("沥青", "BU");
        fmap.put("燃油", "FU"); fmap.put("乙二醇", "EG");
        fmap.put("玉米", "C"); fmap.put("豆一", "A");
        fmap.put("豆粕", "M"); fmap.put("豆油", "Y");
        fmap.put("棕榈油", "P"); fmap.put("白糖", "SR");
        fmap.put("棉花", "CF"); fmap.put("菜油", "OI");
        fmap.put("菜粕", "RM"); fmap.put("鸡蛋", "JD");
        fmap.put("IF", "IF"); fmap.put("IC", "IC");
        fmap.put("IH", "IH"); fmap.put("IM", "IM");
        fmap.put("AU", "AU"); fmap.put("AG", "AG");
        fmap.put("CU", "CU"); fmap.put("RB", "RB");
        fmap.put("SC", "SC"); fmap.put("I", "I");
        fmap.put("MA", "MA"); fmap.put("TA", "TA");
        fmap.put("IO", "IO"); fmap.put("MO", "MO");
        fmap.put("沪深300期货", "IF"); fmap.put("上证50期货", "IH");
        fmap.put("中证500期货", "IC"); fmap.put("中证1000期货", "IM");
        FUTURES_MAP = Map.copyOf(fmap);
        FUTURES_BASES = Set.copyOf(fmap.values());
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KLineService() {
        this.restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public static class KLineData {
        private final String day;
        private final double open;
        private final double high;
        private final double low;
        private final double close;
        private final double volume;

        public KLineData(String day, double open, double high, double low, double close, double volume) {
            this.day = day; this.open = open; this.high = high;
            this.low = low; this.close = close; this.volume = volume;
        }

        public String getDay() { return day; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
        public double getVolume() { return volume; }
    }

    /** 获取股票日 K 线（新浪接口，datalen 限制 20~500）。 */
    public List<KLineData> fetchDailyKLine(String input, int datalen) {
        String symbol = resolveSymbol(input);
        if (symbol == null) return null;

        int len = Math.max(20, Math.min(datalen, 500));

        return fetchSinaKLine(symbol, len);
    }

    /** 调用新浪 K 线接口并解析。 */
    private List<KLineData> fetchSinaKLine(String symbol, int datalen) {
        try {
            String url = SINA_KLINE_API + "?symbol=" + symbol + "&scale=240&ma=no&datalen=" + datalen;
            log.info("新浪K线请求: {}", symbol);

            byte[] respBytes = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);

            if (respBytes == null) return null;
            String text = new String(respBytes, java.nio.charset.StandardCharsets.UTF_8);

            JsonNode arr = objectMapper.readTree(text);
            if (!arr.isArray() || arr.isEmpty()) return null;

            List<KLineData> result = new ArrayList<>();
            for (JsonNode node : arr) {
                String day = node.path("day").asText();
                double open = node.path("open").asDouble();
                double high = node.path("high").asDouble();
                double low = node.path("low").asDouble();
                double close = node.path("close").asDouble();
                double volume = node.path("volume").asDouble();
                result.add(new KLineData(day, open, high, low, close, volume));
            }
            return result;
        } catch (Exception e) {
            log.warn("新浪K线接口失败: {}", e.getMessage());
            return null;
        }
    }

    /** 获取期货主力连续合约的日 K 线。 */
    public List<KLineData> fetchFuturesDailyKLine(String input, int datalen) {
        String base = resolveFuturesBase(input);
        if (base == null) return null;

        try {
            int len = Math.max(20, Math.min(datalen, 500));
            String symbol = base + "0";
            String url = SINA_FUTURES_KLINE_API + "?symbol=" + symbol + "&datalen=" + len;
            log.info("新浪期货K线请求: {}", symbol);

            byte[] respBytes = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);

            String text = new String(respBytes, java.nio.charset.StandardCharsets.UTF_8);

            JsonNode arr = objectMapper.readTree(text);
            if (!arr.isArray() || arr.isEmpty()) return null;

            List<KLineData> result = new ArrayList<>();
            for (JsonNode node : arr) {
                String day = node.get(0).asText();
                double open = node.get(1).asDouble();
                double high = node.get(2).asDouble();
                double low = node.get(3).asDouble();
                double close = node.get(4).asDouble();
                double volume = node.get(5).asDouble();
                result.add(new KLineData(day, open, high, low, close, volume));
            }
            return result;
        } catch (Exception e) {
            log.warn("新浪期货K线接口失败: {}", e.getMessage());
            return null;
        }
    }

    /** 判断输入是否为期货品种。 */
    public boolean isFutures(String input) {
        if (input == null || input.isBlank()) return false;
        String t = input.trim().toUpperCase();
        if (t.matches("^[A-Z]{1,2}888$")) return true;
        if (FUTURES_MAP.containsKey(input.trim())) return true;
        String mapped = StockCodes.byName(input.trim());
        if (mapped != null && FUTURES_BASES.contains(mapped)) return true;
        return false;
    }

    /** 把品种名解析为期货基础代码（如 螺纹钢→RB）。 */
    private String resolveFuturesBase(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();

        String mapped = FUTURES_MAP.get(trimmed);
        if (mapped != null) return mapped;

        if (trimmed.matches("(?i)^[A-Z]{1,2}888$")) {
            return trimmed.substring(0, trimmed.length() - 3).toUpperCase();
        }

        mapped = StockCodes.byName(trimmed);
        if (mapped != null && FUTURES_BASES.contains(mapped)) {
            return mapped;
        }

        return null;
    }

    /** 把名称/代码解析为新浪格式代码。 */
    public String resolveSymbol(String input) {
        if (input == null || input.isBlank()) return null;

        String trimmed = input.trim();

        // 已有 sh/sz 前缀
        if (trimmed.matches("(?i)^(?:sh|sz)[0-9]{6}$")) {
            return trimmed.toLowerCase();
        }

        // 名称映射(A 股才支持 K 线)
        String mapped = StockCodes.byName(trimmed);
        if (mapped != null && (mapped.startsWith("sh") || mapped.startsWith("sz"))) {
            return mapped;
        }

        // 纯数字 6 位
        if (trimmed.matches("^\\d{6}$")) {
            return switch (trimmed.charAt(0)) {
                case '6', '5' -> "sh" + trimmed;
                case '0', '3', '1' -> "sz" + trimmed;
                case '8', '4' -> null; // 北交所暂不支持
                default -> null;
            };
        }

        return null;
    }
}
