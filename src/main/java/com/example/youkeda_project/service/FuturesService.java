package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 期货查询服务，接入新浪/腾讯公开接口。
 * <p>
 * 支持国际期货（hf_ 前缀，如 hf_CL 原油）、国内期货（nf_ 前缀，如 nf_RB0 螺纹钢）。
 * 优先使用新浪接口，失败时自动回退到腾讯接口（仅国际期货支持）。
 * <p>
 * 内置 60 秒短缓存，避免短时间内重复请求同一品种。
 */
@Service
public class FuturesService {

    private static final Logger log = LoggerFactory.getLogger(FuturesService.class);

    private static final String SINA_API = "http://hq.sinajs.cn/list=";
    private static final String TENCENT_API = "http://qt.gtimg.cn/q=";
    private static final long CACHE_TTL_MS = 60_000;
    private static final long KLINE_CACHE_TTL_MS = 300_000; // 5分钟K线缓存

    private final RestClient restClient;

    /** 短缓存: code → {timestamp, result} */
    private final ConcurrentHashMap<String, long[]> cacheTs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> cacheResult = new ConcurrentHashMap<>();

    // K线数据缓存
    private final ConcurrentHashMap<String, long[]> klineCacheTs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<KlineData>> klineCacheData = new ConcurrentHashMap<>();

    /** 中文名称 → 新浪代码映射（国际期货 20+ 品种） */
    private static final Map<String, String> INTERNATIONAL_MAP;

    /** 中文名称 → 新浪代码映射（国内期货 40+ 品种） */
    private static final Map<String, String> DOMESTIC_MAP;

    static {
        Map<String, String> intl = new java.util.HashMap<>();
        // === 国际期货 · 能源 ===
        intl.put("原油", "hf_CL");
        intl.put("wti原油", "hf_CL");
        intl.put("wti", "hf_CL");
        intl.put("纽约原油", "hf_CL");
        intl.put("布伦特原油", "hf_OIL");
        intl.put("天然气", "hf_NG");
        intl.put("天然气连续", "hf_NG");
        intl.put("汽油", "hf_HO");
        intl.put("取暖油", "hf_HO");
        // === 国际期货 · 贵金属 ===
        intl.put("黄金", "hf_GC");
        intl.put("纽约黄金", "hf_GC");
        intl.put("comex黄金", "hf_GC");
        intl.put("白银", "hf_SI");
        intl.put("纽约白银", "hf_SI");
        intl.put("comex白银", "hf_SI");
        intl.put("铂金", "hf_PL");
        intl.put("钯金", "hf_PA");
        // === 国际期货 · 金属 ===
        intl.put("铜", "hf_HG");
        intl.put("纽约铜", "hf_HG");
        intl.put("comex铜", "hf_HG");
        // === 国际期货 · 农产品 ===
        intl.put("玉米", "hf_ZC");
        intl.put("小麦", "hf_ZW");
        intl.put("大豆", "hf_ZS");
        intl.put("豆油", "hf_ZL");
        intl.put("棉花", "hf_CT");
        intl.put("糖", "hf_SB");
        intl.put("咖啡", "hf_KC");
        intl.put("可可", "hf_CC");
        intl.put("稻谷", "hf_ZR");
        INTERNATIONAL_MAP = Map.copyOf(intl);

        Map<String, String> dom = new java.util.HashMap<>();
        // === 国内期货 · 黑色系 ===
        dom.put("螺纹钢", "nf_RB0");
        dom.put("螺纹", "nf_RB0");
        dom.put("热轧卷板", "nf_HC0");
        dom.put("热卷", "nf_HC0");
        dom.put("线材", "nf_WR0");
        dom.put("不锈钢", "nf_SS0");
        dom.put("铁矿石", "nf_I0");
        dom.put("铁矿", "nf_I0");
        dom.put("焦炭", "nf_J0");
        dom.put("焦煤", "nf_JM0");
        dom.put("硅铁", "nf_SF0");
        dom.put("锰硅", "nf_SM0");
        // === 国内期货 · 有色金属 ===
        dom.put("沪铜", "nf_CU0");
        dom.put("铜", "nf_CU0");
        dom.put("沪铝", "nf_AL0");
        dom.put("铝", "nf_AL0");
        dom.put("沪锌", "nf_ZN0");
        dom.put("锌", "nf_ZN0");
        dom.put("沪铅", "nf_PB0");
        dom.put("铅", "nf_PB0");
        dom.put("沪镍", "nf_NI0");
        dom.put("镍", "nf_NI0");
        dom.put("沪锡", "nf_SN0");
        dom.put("锡", "nf_SN0");
        dom.put("氧化铝", "nf_AO0");
        dom.put("沪金", "nf_AU0");
        dom.put("黄金", "nf_AU0");
        dom.put("沪银", "nf_AG0");
        dom.put("白银", "nf_AG0");
        // === 国内期货 · 能源化工 ===
        dom.put("sc原油", "nf_SC0");
        dom.put("燃料油", "nf_FU0");
        dom.put("低硫燃油", "nf_LU0");
        dom.put("沥青", "nf_BU0");
        dom.put("液化气", "nf_PG0");
        dom.put("lpg", "nf_PG0");
        dom.put("甲醇", "nf_MA0");
        dom.put("pta", "nf_TA0");
        dom.put("乙二醇", "nf_EG0");
        dom.put("纯碱", "nf_SA0");
        dom.put("玻璃", "nf_FG0");
        dom.put("尿素", "nf_UR0");
        dom.put("橡胶", "nf_RU0");
        dom.put("纸浆", "nf_SP0");
        dom.put("苯乙烯", "nf_EB0");
        dom.put("短纤", "nf_PF0");
        dom.put("PVC", "nf_V0");
        dom.put("聚丙烯", "nf_PP0");
        dom.put("塑料", "nf_L0");
        // === 国内期货 · 农产品 ===
        dom.put("豆一", "nf_A0");
        dom.put("黄大豆", "nf_A0");
        dom.put("豆粕", "nf_A0");
        dom.put("豆二", "nf_B0");
        dom.put("棕榈油", "nf_P0");
        dom.put("菜油", "nf_OI0");
        dom.put("菜粕", "nf_RM0");
        dom.put("花生", "nf_PK0");
        dom.put("玉米", "nf_C0");
        dom.put("玉米淀粉", "nf_CS0");
        dom.put("粳米", "nf_JR0");
        dom.put("苹果", "nf_AP0");
        dom.put("红枣", "nf_CJ0");
        dom.put("生猪", "nf_LH0");
        dom.put("鸡蛋", "nf_JD0");
        dom.put("白糖", "nf_SR0");
        dom.put("棉花", "nf_CF0");
        dom.put("棉纱", "nf_CY0");
        // === 国内期货 · 股指期货 ===
        dom.put("沪深300", "nf_IF0");
        dom.put("if", "nf_IF0");
        dom.put("沪深300期货", "nf_IF0");
        dom.put("中证500", "nf_IC0");
        dom.put("ic", "nf_IC0");
        dom.put("中证500期货", "nf_IC0");
        dom.put("上证50", "nf_IH0");
        dom.put("ih", "nf_IH0");
        dom.put("上证50期货", "nf_IH0");
        dom.put("中证1000", "nf_IM0");
        dom.put("im", "nf_IM0");
        dom.put("中证1000期货", "nf_IM0");
        // === 国内期货 · 国债期货 ===
        dom.put("十年国债", "nf_T0");
        dom.put("十年期国债", "nf_T0");
        dom.put("五年国债", "nf_TF0");
        dom.put("五年期国债", "nf_TF0");
        dom.put("两年国债", "nf_TS0");
        dom.put("两年期国债", "nf_TS0");
        DOMESTIC_MAP = Map.copyOf(dom);
    }

    public FuturesService() {
        this.restClient = RestClient.create();
    }

    /**
     * 查询期货行情。支持中文名称、带前缀代码、纯字母代码。
     *
     * @param input 期货名称或代码，如 "原油"、"hf_CL"、"螺纹钢"、"nf_RB0"、"CL"
     * @return 格式化的行情字符串
     */
    public String queryFutures(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "错误: 请输入期货名称或代码\n"
                + "示例: 原油 / 黄金 / 螺纹钢 / hf_CL / nf_RB0";
        }

        String trimmed = input.trim();

        // 1. 名称映射查找（国内优先）
        String fullCode = null;
        String displayName = trimmed;
        String market = null;

        // 精确匹配（国内优先）
        fullCode = DOMESTIC_MAP.get(trimmed);
        if (fullCode != null) { market = "domestic"; }

        if (fullCode == null) {
            fullCode = INTERNATIONAL_MAP.get(trimmed);
            if (fullCode != null) { market = "international"; }
        }

        // 小写匹配
        if (fullCode == null) {
            fullCode = DOMESTIC_MAP.get(trimmed.toLowerCase());
            if (fullCode != null) { market = "domestic"; }
        }
        if (fullCode == null) {
            fullCode = INTERNATIONAL_MAP.get(trimmed.toLowerCase());
            if (fullCode != null) { market = "international"; }
        }

        if (fullCode != null) {
            return fetchWithCache(fullCode, displayName, market);
        }

        // 2. 已带 hf_ 前缀 → 国际期货
        if (trimmed.regionMatches(true, 0, "hf_", 0, 3)) {
            return fetchWithCache(trimmed.toLowerCase(), trimmed, "international");
        }

        // 3. 已带 nf_ 前缀 → 国内期货
        if (trimmed.regionMatches(true, 0, "nf_", 0, 3)) {
            return fetchWithCache(trimmed.toLowerCase(), trimmed, "domestic");
        }

        // 4. 纯字母代码（2~3位）→ 国际期货（自动补 hf_ 前缀）
        if (trimmed.matches("(?i)^[a-z]{2,3}$")) {
            return fetchWithCache("hf_" + trimmed.toLowerCase(), trimmed, "international");
        }

        // 5. 国内品种代码（如 RB0、IF0、CU0）→ 自动补 nf_ 前缀
        if (trimmed.matches("(?i)^[a-z]{1,3}\\d$")) {
            return fetchWithCache("nf_" + trimmed.toLowerCase(), trimmed, "domestic");
        }

        // 6. 国内品种代码带数字后缀（如 RB2510）→ 自动补 nf_ 前缀
        if (trimmed.matches("(?i)^[a-z]{1,3}\\d{3,4}$")) {
            return fetchWithCache("nf_" + trimmed.toLowerCase(), trimmed, "domestic");
        }

        return "错误: 不支持的期货 '" + trimmed + "'\n"
            + "支持: 中文名称（原油/黄金/螺纹钢）、代码（hf_CL/nf_RB0/CL）";
    }

    // ==================== 缓存 ====================

    private String fetchWithCache(String fullCode, String displayName, String market) {
        long now = System.currentTimeMillis();
        long[] ts = cacheTs.get(fullCode);
        if (ts != null && now - ts[0] < CACHE_TTL_MS) {
            String cached = cacheResult.get(fullCode);
            if (cached != null) {
                log.debug("期货缓存命中: {}", fullCode);
                return cached;
            }
        }

        String result = fetchFuturesData(fullCode, displayName, market);
        cacheTs.put(fullCode, new long[]{now});
        cacheResult.put(fullCode, result);
        return result;
    }

    // ==================== 数据拉取 ====================

    private String fetchFuturesData(String fullCode, String displayName, String market) {
        log.info("查询期货: code={}, display={}, market={}", fullCode, displayName, market);

        try {
            return fetchFromSina(fullCode, displayName, market);
        } catch (Exception e) {
            log.warn("新浪期货接口查询失败({}): {}, 回退到腾讯接口", fullCode, e.getMessage());
            try {
                return fetchFromTencent(fullCode, displayName, market);
            } catch (Exception e2) {
                log.error("腾讯期货接口也查询失败({}): {}", fullCode, e2.getMessage());
                return "错误: 期货查询失败，两个接口均不可用\n品种: " + displayName;
            }
        }
    }

    // ==================== 新浪接口 ====================

    private String fetchFromSina(String fullCode, String displayName, String market) {
        String response = restClient.get()
            .uri(SINA_API + fullCode)
            .header("Referer", "https://finance.sina.com.cn")
            .retrieve()
            .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RuntimeException("新浪返回空数据");
        }

        log.info("新浪期货响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) {
            throw new RuntimeException("新浪响应格式异常: 无引号包裹");
        }
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty() || data.isBlank()) {
            throw new RuntimeException("新浪返回空行情数据，期货代码可能无效或无数据");
        }

        String[] fields = data.split(",", -1);

        if ("international".equals(market)) {
            return parseSinaInternational(fullCode, fields, displayName);
        } else {
            return parseSinaDomestic(fullCode, fields, displayName);
        }
    }

    /**
     * 解析新浪国际期货响应（hf_ 前缀）。
     * 字段: [0]最新价, [1]??, [2]买入价, [3]卖出价, [4]最高价, [5]最低价,
     *       [6]时间, [7]昨收, [8]开盘价, [9-11]??, [12]日期, [13]名称, [14]??
     */
    private String parseSinaInternational(String code, String[] fields, String displayName) {
        if (fields.length < 14) {
            throw new RuntimeException("国际期货字段不足: " + fields.length);
        }

        String name = fields[13].trim();
        if (name.isEmpty()) name = displayName;
        String price = fields[0];
        String high = fields[4];
        String low = fields[5];
        String time = fields[6];
        String prevClose = fields[7];
        String open = fields[8];
        String date = fields[12];

        String category = getInternationalCategory(code);

        return formatFuturesInfo(name, code, price,
            prevClose, open, high, low, date, time, "国际期货 · " + category, "$");
    }

    /**
     * 解析新浪国内期货响应（nf_ 前缀）。
     * 所有国内期货（商品/股指/国债）格式一致：
     * [0]名称, [1]时间(HHmmss), [2]最新价, [3]开盘价, [4]昨收,
     * [5]??, [6]最高价, [7]最低价, [8]买入价, [9]卖出价,
     * [10-12]??, [13]成交量, [14]成交额, [15-16]??, [17]日期(YYYY-MM-DD), ...
     */
    private String parseSinaDomestic(String code, String[] fields, String displayName) {
        if (fields.length < 8) {
            throw new RuntimeException("国内期货字段不足: " + fields.length);
        }

        String name = fields[0].trim();
        if (name.isEmpty()) name = displayName;
        String time = fields.length > 1 ? fields[1].trim() : "";
        String price = fields[2];
        String open = fields[3];
        String prevClose = fields[4];
        String high = fields[6];
        String low = fields[7];

        // 日期统一在 [17]
        String date = "";
        if (fields.length > 17) {
            String d = fields[17].trim();
            if (d.matches("\\d{4}-\\d{2}-\\d{2}")) {
                date = d;
            }
        }

        String category = getDomesticCategory(code);

        return formatFuturesInfo(name, code,
            price, prevClose, open, high, low, date, time,
            "国内期货 · " + category, "");
    }

    // ==================== 腾讯接口（回退） ====================

    /**
     * 腾讯期货接口。仅支持国际期货（hf_ 前缀）。
     */
    private String fetchFromTencent(String fullCode, String displayName, String market) {
        if (!"international".equals(market)) {
            throw new RuntimeException("腾讯接口仅支持国际期货");
        }

        String response = restClient.get()
            .uri(TENCENT_API + fullCode)
            .retrieve()
            .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RuntimeException("腾讯返回空数据");
        }

        log.info("腾讯期货响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) {
            throw new RuntimeException("腾讯响应格式异常");
        }
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty()) {
            throw new RuntimeException("腾讯返回空行情数据");
        }

        String[] fields = data.split(",", -1);
        if (fields.length < 14) {
            throw new RuntimeException("腾讯期货字段不足: " + fields.length);
        }

        String price = fields[0];
        String high = fields[4];
        String low = fields[5];
        String time = fields[6];
        String prevClose = fields[7];
        String open = fields[8];
        String date = fields[12];
        String name = fields[13];

        String category = getInternationalCategory(fullCode);
        return formatFuturesInfo(name.isEmpty() ? displayName : name, fullCode,
            price, prevClose, open, high, low, date, time,
            "国际期货 · " + category, "$");
    }

    // ==================== 品种分类 ====================

    private String getInternationalCategory(String code) {
        return switch (code) {
            case "hf_CL", "hf_NG", "hf_HO", "hf_OIL" -> "能源";
            case "hf_GC", "hf_SI", "hf_PL", "hf_PA" -> "贵金属";
            case "hf_HG" -> "金属";
            case "hf_ZC", "hf_ZW", "hf_ZS", "hf_ZM", "hf_ZL",
                 "hf_CT", "hf_SB", "hf_KC", "hf_CC", "hf_ZR" -> "农产品";
            default -> "国际";
        };
    }

    private String getDomesticCategory(String code) {
        // 去掉 nf_ 前缀再匹配
        String c = code.startsWith("nf_") ? code.substring(3) : code;
        return switch (c) {
            case "RB0", "HC0", "WR0", "SS0", "I0",
                 "J0", "JM0", "SF0", "SM0" -> "黑色系";
            case "CU0", "AL0", "ZN0", "PB0",
                 "NI0", "SN0", "AO0" -> "有色金属";
            case "SC0", "FU0", "LU0", "BU0", "PG0",
                 "MA0", "TA0", "EG0", "SA0", "FG0",
                 "UR0", "RU0", "SP0", "EB0", "PF0",
                 "V0", "PP0", "L0" -> "能源化工";
            case "A0", "B0", "P0", "OI0", "RM0",
                 "PK0", "C0", "CS0", "JR0",
                 "AP0", "CJ0", "LH0", "JD0",
                 "SR0", "CF0", "CY0" -> "农产品";
            case "IF0", "IC0", "IH0", "IM0" -> "金融期货";
            case "T0", "TF0", "TS0" -> "国债期货";
            default -> "国内";
        };
    }

    // ==================== 格式化输出 ====================

    private String formatFuturesInfo(String name, String code, String currentPrice,
                                      String prevClose, String open, String high,
                                      String low, String date, String time,
                                      String marketLabel, String currencyPrefix) {
        try {
            BigDecimal price = new BigDecimal(currentPrice.trim());
            BigDecimal change = BigDecimal.ZERO;
            BigDecimal changePct = BigDecimal.ZERO;
            String direction = "📈";
            String sign = "";

            if (prevClose != null && !prevClose.isBlank()) {
                String ps = prevClose.trim();
                if (!"0".equals(ps) && !"0.000".equals(ps)) {
                    BigDecimal prev = new BigDecimal(ps);
                    if (prev.compareTo(BigDecimal.ZERO) > 0) {
                        change = price.subtract(prev);
                        changePct = change.divide(prev, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                        direction = change.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                        sign = change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(direction).append(" ").append(name).append("（").append(code).append("）\n");
            sb.append("  品种: ").append(marketLabel).append("\n");
            sb.append("  最新价: ").append(currencyPrefix).append(price).append("\n");

            if (change.compareTo(BigDecimal.ZERO) != 0) {
                sb.append("  涨跌额: ").append(sign)
                    .append(change.setScale(2, RoundingMode.HALF_UP)).append("\n");
                sb.append("  涨跌幅: ").append(sign)
                    .append(changePct.setScale(2, RoundingMode.HALF_UP)).append("%\n");
            }

            if (isNonZero(open)) {
                sb.append("  今开: ").append(currencyPrefix).append(open.trim()).append("\n");
            }
            if (isNonZero(high)) {
                sb.append("  最高: ").append(currencyPrefix).append(high.trim()).append("\n");
            }
            if (isNonZero(low)) {
                sb.append("  最低: ").append(currencyPrefix).append(low.trim()).append("\n");
            }
            if (date != null && !date.isBlank()) {
                sb.append("  日期: ").append(date.trim());
                if (time != null && !time.isBlank() && !time.equals("0")) {
                    String t = time.trim();
                    if (t.length() == 6) {
                        t = t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4);
                    }
                    sb.append(" ").append(t);
                }
            }
            return sb.toString();
        } catch (NumberFormatException e) {
            log.warn("期货价格格式化失败: price={}, prevClose={}", currentPrice, prevClose);
            return name + "（" + code + "）\n  最新价: " + currentPrice;
        }
    }

    private boolean isNonZero(String val) {
        if (val == null || val.isBlank()) return false;
        String s = val.trim();
        return !"0".equals(s) && !"0.000".equals(s) && !"0.00".equals(s);
    }

    // ==================== 历史 K 线数据（新浪） ====================

    /**
     * 获取期货历史K线数据（带缓存）。
     * 国内期货使用新浪InnerFutures API，国际期货使用新浪GlobalFutures API。
     */
    public List<KlineData> getHistoricalKline(String input, int days) {
        String code = resolveFuturesCode(input);
        if (code == null) return List.of();

        // 检查缓存
        String cacheKey = code + ":" + days;
        long now = System.currentTimeMillis();
        long[] ts = klineCacheTs.get(cacheKey);
        if (ts != null && now - ts[0] < KLINE_CACHE_TTL_MS) {
            List<KlineData> cached = klineCacheData.get(cacheKey);
            if (cached != null) {
                log.debug("期货K线缓存命中: key={}", cacheKey);
                return cached;
            }
        }

        // 获取数据
        List<KlineData> result;
        if (code.startsWith("hf_")) {
            result = getInternationalFuturesKline(code, days);
        } else if (code.startsWith("nf_")) {
            result = getDomesticFuturesKline(code, days);
        } else {
            result = List.of();
        }

        // 写入缓存
        if (!result.isEmpty()) {
            klineCacheTs.put(cacheKey, new long[]{now});
            klineCacheData.put(cacheKey, result);
        }

        return result;
    }

    /** 国内期货K线（新浪InnerFutures API） */
    private List<KlineData> getDomesticFuturesKline(String code, int days) {
        String symbol = code.substring(3); // 去掉 nf_ 前缀

        String url = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20_"
            + code + "=/InnerFuturesNewService.getDailyKLine?symbol=" + symbol + "&datalen=" + (days + 20);

        try {
            String response = restClient.get()
                .uri(url)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve()
                .body(String.class);

            if (response == null || response.isBlank()) return List.of();

            int start = response.indexOf("([");
            int end = response.indexOf("])");
            if (start == -1 || end == -1) return List.of();

            String json = response.substring(start + 1, end + 1);
            if (json.isEmpty() || "null".equals(json)) return List.of();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode arr = mapper.readTree(json);

            List<KlineData> result = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : arr) {
                String date = item.path("d").asText("");
                String open = item.path("o").asText("");
                String high = item.path("h").asText("");
                String low = item.path("l").asText("");
                String close = item.path("c").asText("");
                String volume = item.path("v").asText("");
                if (!date.isEmpty() && !close.isEmpty()) {
                    result.add(new KlineData(date, open, high, low, close, volume));
                }
            }

            if (result.size() > days) {
                result = new ArrayList<>(result.subList(result.size() - days, result.size()));
            }

            log.info("国内期货K线获取成功: code={}, 数据量={}", code, result.size());
            return result;
        } catch (Exception e) {
            log.warn("国内期货K线获取失败: code={}, error={}", code, e.getMessage());
            return List.of();
        }
    }

    /** 国际期货K线（新浪GlobalFutures API） */
    private List<KlineData> getInternationalFuturesKline(String code, int days) {
        String symbol = code.substring(3); // 去掉 hf_ 前缀

        String url = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20_"
            + code + "=/GlobalFuturesService.getGlobalFuturesDailyKLine?symbol=" + symbol
            + "&_=1&_=" + System.currentTimeMillis();

        try {
            String response = restClient.get()
                .uri(url)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve()
                .body(String.class);

            if (response == null || response.isBlank()) return List.of();

            int start = response.indexOf("([");
            int end = response.indexOf("])");
            if (start == -1 || end == -1) return List.of();

            String json = response.substring(start + 1, end + 1);
            if (json.isEmpty() || "null".equals(json)) return List.of();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode arr = mapper.readTree(json);

            List<KlineData> result = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : arr) {
                String date = item.path("date").asText("");
                String open = item.path("open").asText("");
                String high = item.path("high").asText("");
                String low = item.path("low").asText("");
                String close = item.path("close").asText("");
                String volume = item.path("volume").asText("0");
                if (!date.isEmpty() && !close.isEmpty()) {
                    result.add(new KlineData(date, open, high, low, close, volume));
                }
            }

            if (result.size() > days) {
                result = new ArrayList<>(result.subList(result.size() - days, result.size()));
            }

            log.info("国际期货K线获取成功: code={}, 数据量={}", code, result.size());
            return result;
        } catch (Exception e) {
            log.warn("国际期货K线获取失败: code={}, error={}", code, e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析期货代码（复用 queryFutures 的逻辑）
     */
    private String resolveFuturesCode(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();

        // 名称查找
        String code = DOMESTIC_MAP.get(trimmed);
        if (code != null) return code;
        code = INTERNATIONAL_MAP.get(trimmed);
        if (code != null) return code;
        code = DOMESTIC_MAP.get(trimmed.toLowerCase());
        if (code != null) return code;
        code = INTERNATIONAL_MAP.get(trimmed.toLowerCase());
        if (code != null) return code;

        // 已带前缀
        if (trimmed.regionMatches(true, 0, "hf_", 0, 3)) return trimmed.toLowerCase();
        if (trimmed.regionMatches(true, 0, "nf_", 0, 3)) return trimmed.toLowerCase();

        // 纯字母代码
        if (trimmed.matches("(?i)^[a-z]{2,3}$")) return "hf_" + trimmed.toLowerCase();
        if (trimmed.matches("(?i)^[a-z]{1,3}\\d$")) return "nf_" + trimmed.toLowerCase();
        if (trimmed.matches("(?i)^[a-z]{1,3}\\d{3,4}$")) return "nf_" + trimmed.toLowerCase();

        return null;
    }

    /** K 线数据 */
    public record KlineData(String date, String open, String high, String low, String close, String volume) {}
}
