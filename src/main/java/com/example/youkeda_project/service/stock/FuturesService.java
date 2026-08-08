package com.example.youkeda_project.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.youkeda_project.util.FormatUtil;
import com.example.youkeda_project.util.StockQuoteParser;
import com.example.youkeda_project.util.TtlCache;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private final RestClient restClient;

    /** 短缓存: code → {timestamp, result} */
    private final TtlCache<String> cache = new TtlCache<>(CACHE_TTL_MS);

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
        intl.put("豆粕", "hf_ZM");
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

        // 2. 已带 hf_ 前缀 → 国际期货(新浪代码需大写)
        if (trimmed.regionMatches(true, 0, "hf_", 0, 3)) {
            String code = trimmed.length() > 3 ? "hf_" + trimmed.substring(3).toUpperCase() : trimmed;
            return fetchWithCache(code, trimmed, "international");
        }

        // 3. 已带 nf_ 前缀 → 国内期货(新浪代码需大写)
        if (trimmed.regionMatches(true, 0, "nf_", 0, 3)) {
            String code = trimmed.length() > 3 ? "nf_" + trimmed.substring(3).toUpperCase() : trimmed;
            return fetchWithCache(code, trimmed, "domestic");
        }

        // 4. 纯字母代码（2~3位）→ 国际期货（自动补 hf_ 前缀）
        if (trimmed.matches("(?i)^[a-z]{2,3}$")) {
            return fetchWithCache("hf_" + trimmed.toUpperCase(), trimmed, "international");
        }

        // 5. 国内品种代码（如 RB0、IF0、CU0）→ 自动补 nf_ 前缀
        if (trimmed.matches("(?i)^[a-z]{1,3}\\d$")) {
            return fetchWithCache("nf_" + trimmed.toUpperCase(), trimmed, "domestic");
        }

        // 6. 国内品种代码带数字后缀（如 RB2510）→ 自动补 nf_ 前缀
        if (trimmed.matches("(?i)^[a-z]{1,3}\\d{3,4}$")) {
            return fetchWithCache("nf_" + trimmed.toUpperCase(), trimmed, "domestic");
        }

        return "错误: 不支持的期货 '" + trimmed + "'\n"
            + "支持: 中文名称（原油/黄金/螺纹钢）、代码（hf_CL/nf_RB0/CL）";
    }

    // ==================== 缓存 ====================

    private String fetchWithCache(String fullCode, String displayName, String market) {
        String cached = cache.get(fullCode);
        if (cached != null) {
            log.debug("期货缓存命中: {}", fullCode);
            return cached;
        }

        String result = fetchFuturesData(fullCode, displayName, market);
        cache.put(fullCode, result);
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

        String[] fields = StockQuoteParser.extractQuotedFields(response, ",");
        if (fields == null) {
            if (response.indexOf('"') == -1) {
                throw new RuntimeException("新浪响应格式异常: 无引号包裹");
            }
            throw new RuntimeException("新浪返回空行情数据，期货代码可能无效或无数据");
        }
        if (fields.length == 1 && fields[0].isBlank()) {
            throw new RuntimeException("新浪返回空行情数据，期货代码可能无效或无数据");
        }

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

        String[] fields = StockQuoteParser.extractQuotedFields(response, ",");
        if (fields == null) {
            if (response.indexOf('"') == -1) {
                throw new RuntimeException("腾讯响应格式异常");
            }
            throw new RuntimeException("腾讯返回空行情数据");
        }

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
            FormatUtil.ChangeInfo ci = FormatUtil.calcChange(price, prevClose);

            StringBuilder sb = new StringBuilder();
            sb.append(ci.direction()).append(" ").append(name).append("（").append(code).append("）\n");
            sb.append("  品种: ").append(marketLabel).append("\n");
            sb.append("  最新价: ").append(currencyPrefix).append(price).append("\n");

            if (ci.change().compareTo(BigDecimal.ZERO) != 0) {
                sb.append("  涨跌额: ").append(ci.sign())
                    .append(ci.change().setScale(2, RoundingMode.HALF_UP)).append("\n");
                sb.append("  涨跌幅: ").append(ci.sign())
                    .append(ci.changePct().setScale(2, RoundingMode.HALF_UP)).append("%\n");
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
                String t = "";
                if (time != null && !time.isBlank() && !time.equals("0")) {
                    t = time.trim();
                    if (t.length() == 6) {
                        t = t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4);
                    }
                }
                sb.append("  🕐 数据时间: ").append(FormatUtil.formatQuoteDate(date.trim(), t));
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

    /**
     * 查询某品种全部在交易合约列表(按成交量排序,标主力/次主力).
     * 国内期货自动探测未来常见月份;国际期货返回常见连续月份.
     *
     * @param variety 品种名或代码,如 "螺纹钢"/"RB"/"nf_RB0"
     * @return 格式化合约列表文本;无法识别返回 null
     */
    public String listContracts(String variety) {
        if (variety == null || variety.isBlank()) return null;
        String trimmed = variety.trim();

        // 解析品种基代码(RB)和市场类型
        String baseCode = null;
        boolean international = false;
        String mapped = DOMESTIC_MAP.get(trimmed);
        if (mapped == null) mapped = DOMESTIC_MAP.get(trimmed.toLowerCase());
        if (mapped != null && mapped.matches("nf_([A-Za-z]+)0")) {
            baseCode = mapped.substring(3, mapped.length() - 1); // nf_RB0 -> RB
            international = false;
        }
        if (baseCode == null) {
            mapped = INTERNATIONAL_MAP.get(trimmed);
            if (mapped == null) mapped = INTERNATIONAL_MAP.get(trimmed.toLowerCase());
            if (mapped != null && mapped.matches("hf_([A-Za-z]+)")) {
                baseCode = mapped.substring(3);
                international = true;
            }
        }
        // 纯代码如 RB / RB0 / CL
        if (baseCode == null) {
            String t = trimmed.toUpperCase().replaceAll("^NF_", "").replaceAll("^HF_", "").replaceAll("0$", "");
            if (t.matches("^[A-Z]{1,3}$")) {
                baseCode = t;
                international = trimmed.toUpperCase().startsWith("HF_");
            }
        }
        if (baseCode == null) return null;

        StringBuilder result = new StringBuilder();
        if (!international) {
            result.append(probeDomesticContracts(baseCode));
        } else {
            result.append(probeInternationalContracts(baseCode));
        }
        return result.length() == 0 ? null : result.toString();
    }

    /** 探测国内期货在交易合约:生成未来常见月份合约,一次批量查询. */
    private String probeDomesticContracts(String baseCode) {
        int[] months = {1, 3, 5, 7, 8, 9, 10, 11, 12};
        StringBuilder codes = new StringBuilder();
        for (int y = 0; y < 2; y++) {
            int yy = (java.time.LocalDate.now().getYear() + y) % 100;
            for (int m : months) {
                if (codes.length() > 0) codes.append(",");
                codes.append("nf_").append(baseCode).append(String.format("%02d%02d", yy, m));
            }
        }

        // 批量查询,过滤有效合约
        java.util.List<ContractInfo> valid = new ArrayList<>();
        try {
            String url = SINA_API + codes;
            String response = restClient.get().uri(url)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve().body(String.class);
            if (response != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "nf_" + baseCode + "(\\d{4})=\"(.*?)\"").matcher(response);
                while (m.find()) {
                    String month = m.group(1);
                    String data = m.group(2);
                    if (data != null && !data.isEmpty()) {
                        String[] f = data.split(",", -1);
                        String vol = f.length > 13 ? f[13] : "0";
                        String price = f.length > 2 ? f[2] : "";
                        String date = f.length > 17 ? f[17] : "";
                        valid.add(new ContractInfo(baseCode + month, price, vol, date));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("探测国内合约失败: {}", e.getMessage());
            return "";
        }

        if (valid.isEmpty()) return "";
        // 按成交量降序
        valid.sort((a, b) -> Double.compare(parseVol(b.vol), parseVol(a.vol)));

        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(baseCode).append(" 在交易合约（按成交量排序）\n\n");
        for (int i = 0; i < valid.size(); i++) {
            ContractInfo c = valid.get(i);
            String tag = switch (i) {
                case 0 -> "🏆 主力";
                case 1 -> "🥈 次主力";
                case 2 -> "🥉";
                default -> "";
            };
            sb.append(String.format("%s %s  价格:%s  成交量:%s\n",
                tag, c.code, c.price.isEmpty() ? "—" : c.price, fmtVol(c.vol)));
        }
        sb.append("\n回复合约代码（如 ").append(valid.get(0).code).append("）查看行情");
        return sb.toString();
    }

    /** 国际期货合约列表(常见主力月份序列). */
    private String probeInternationalContracts(String baseCode) {
        // 国际期货主要月份: 当前年+下一年, 取 1-12 月中常见主力月
        int[] months = {1, 3, 5, 7, 8, 9, 10, 11, 12};
        StringBuilder codes = new StringBuilder();
        for (int y = 0; y < 2; y++) {
            int yy = (java.time.LocalDate.now().getYear() + y) % 100;
            for (int m : months) {
                if (codes.length() > 0) codes.append(",");
                codes.append("hf_").append(baseCode).append(String.format("%02d%02d", yy, m));
            }
        }
        java.util.List<ContractInfo> valid = new ArrayList<>();
        try {
            String url = SINA_API + codes;
            String response = restClient.get().uri(url)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve().body(String.class);
            if (response != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "hf_" + baseCode + "(\\d{4})=\"(.*?)\"").matcher(response);
                while (m.find()) {
                    String month = m.group(1);
                    String data = m.group(2);
                    if (data != null && !data.isEmpty()) {
                        String[] f = data.split(",", -1);
                        String vol = f.length > 10 ? f[10] : "0";
                        String price = f.length > 0 ? f[0] : "";
                        String date = f.length > 12 ? f[12] : "";
                        valid.add(new ContractInfo(baseCode + month, price, vol, date));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("探测国际合约失败: {}", e.getMessage());
            return "";
        }
        if (valid.isEmpty()) return "";
        valid.sort((a, b) -> Double.compare(parseVol(b.vol), parseVol(a.vol)));
        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(baseCode).append(" 在交易合约（按成交量排序）\n\n");
        for (int i = 0; i < valid.size(); i++) {
            ContractInfo c = valid.get(i);
            String tag = switch (i) {
                case 0 -> "🏆 主力";
                case 1 -> "🥈 次主力";
                case 2 -> "🥉";
                default -> "";
            };
            sb.append(String.format("%s %s  价格:%s  成交量:%s\n",
                tag, c.code, c.price.isEmpty() ? "—" : c.price, fmtVol(c.vol)));
        }
        sb.append("\n回复合约代码（如 ").append(valid.get(0).code).append("）查看行情");
        return sb.toString();
    }

    private double parseVol(String v) {
        try { return Double.parseDouble(v.trim()); } catch (Exception e) { return 0; }
    }

    private String fmtVol(String v) {
        try {
            double d = Double.parseDouble(v.trim());
            if (d >= 10000) return String.format("%.1f万", d / 10000);
            return String.format("%.0f", d);
        } catch (Exception e) { return v; }
    }

    /** 合约信息 */
    private record ContractInfo(String code, String price, String vol, String date) {}
}
