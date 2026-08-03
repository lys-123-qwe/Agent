package com.example.youkeda_project.service.stock;

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
 * 基金查询服务，接入新浪/腾讯公开接口。
 * <p>
 * 支持 ETF（场内基金，sh/sz 前缀，复用股票接口）和开放式基金（f_ 前缀）。
 * 优先使用新浪接口，失败时自动回退到腾讯接口。
 * 内置 60 秒短缓存。
 */
@Service
public class FundService {

    private static final Logger log = LoggerFactory.getLogger(FundService.class);

    private static final String SINA_API = "http://hq.sinajs.cn/list=";
    private static final String TENCENT_API = "http://qt.gtimg.cn/q=";
    private static final long CACHE_TTL_MS = 60_000;
    private static final long NAV_CACHE_TTL_MS = 300_000; // 5分钟净值缓存

    private final RestClient restClient;

    private final ConcurrentHashMap<String, long[]> cacheTs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> cacheResult = new ConcurrentHashMap<>();

    // 历史净值缓存
    private final ConcurrentHashMap<String, long[]> navCacheTs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<NavData>> navCacheData = new ConcurrentHashMap<>();

    /** 中文名称 → 新浪代码映射（ETF 场内基金） */
    private static final Map<String, String> ETF_MAP;

    /** 中文名称 → 新浪代码映射（开放式基金） */
    private static final Map<String, String> OPENEND_MAP;

    static {
        Map<String, String> etf = new java.util.HashMap<>();
        // === 宽基指数 ETF ===
        etf.put("沪深300ETF", "sh510300");
        etf.put("300ETF", "sh510300");
        etf.put("上证50ETF", "sh510050");
        etf.put("50ETF", "sh510050");
        etf.put("中证500ETF", "sh510500");
        etf.put("创业板ETF", "sz159915");
        etf.put("科创50ETF", "sh588000");
        etf.put("中证1000ETF", "sh512100");
        etf.put("创业板50ETF", "sz159949");
        etf.put("MSCI中国ETF", "sh512990");
        etf.put("红利ETF", "sh510880");
        etf.put("恒生ETF", "sz159920");
        etf.put("H股ETF", "sh510900");
        // === 行业/主题 ETF ===
        etf.put("芯片ETF", "sz159995");
        etf.put("半导体ETF", "sh512480");
        etf.put("新能源ETF", "sh516160");
        etf.put("光伏ETF", "sh515790");
        etf.put("电池ETF", "sz159755");
        etf.put("医药ETF", "sh512010");
        etf.put("医疗ETF", "sz159828");
        etf.put("创新药ETF", "sh515120");
        etf.put("消费ETF", "sh159928");
        etf.put("白酒ETF", "sz159869");
        etf.put("食品饮料ETF", "sh515170");
        etf.put("军工ETF", "sh512660");
        etf.put("证券ETF", "sh512880");
        etf.put("银行ETF", "sh512800");
        etf.put("房地产ETF", "sh512200");
        etf.put("人工智能ETF", "sh515070");
        etf.put("机器人ETF", "sh562500");
        etf.put("游戏ETF", "sh516010");
        etf.put("传媒ETF", "sh512980");
        etf.put("新能源车ETF", "sz515030");
        etf.put("碳中和ETF", "sz159790");
        etf.put("稀土ETF", "sh516150");
        etf.put("碳中和ETF基金", "sh516070");
        // === 债券/商品 ETF ===
        etf.put("国债ETF", "sh511010");
        etf.put("黄金ETF", "sh518880");
        etf.put("黄金ETF基金", "sh518800");
        etf.put("豆粕ETF", "sz159985");
        etf.put("有色ETF", "sh512400");
        etf.put("能源化工ETF", "sh159981");
        ETF_MAP = Map.copyOf(etf);

        Map<String, String> open = new java.util.HashMap<>();
        // === 混合型基金 ===
        open.put("易方达蓝筹精选", "f_005827");
        open.put("易方达优质精选", "f_110011");
        open.put("易方达竞争优势企业", "f_010198");
        open.put("中欧医疗健康A", "f_003095");
        open.put("中欧医疗健康", "f_003095");
        open.put("中欧明睿新起点", "f_001811");
        open.put("招商中证白酒指数A", "f_161725");
        open.put("招商中证白酒", "f_161725");
        open.put("白酒基金", "f_161725");
        open.put("景顺长城新兴成长", "f_260108");
        open.put("景顺长城鼎益", "f_162605");
        open.put("富国天惠成长A", "f_161005");
        open.put("富国天惠", "f_161005");
        open.put("兴全合润", "f_163406");
        open.put("兴全趋势投资", "f_163402");
        open.put("交银新成长", "f_519736");
        open.put("银华富裕主题", "f_180012");
        open.put("鹏华匠心精选A", "f_009570");
        open.put("睿远成长价值A", "f_007119");
        open.put("睿远成长价值", "f_007119");
        open.put("泉果旭源三年持有", "f_017219");
        open.put("前海开源沪港深优势精选", "f_001875");
        open.put("华安文体健康", "f_001532");
        open.put("东方红产业升级", "f_000990");
        open.put("交银优势行业", "f_519066");
        // === 指数型基金 ===
        open.put("天弘沪深300A", "f_000961");
        open.put("天弘沪深300", "f_000961");
        open.put("天弘中证500A", "f_000962");
        open.put("天弘中证500", "f_000962");
        open.put("易方达上证50联接A", "f_110003");
        open.put("华安创业板50指数", "f_160223");
        open.put("易方达创业板ETF联接A", "f_110026");
        open.put("华泰柏瑞沪深300ETF联接A", "f_460300");
        open.put("嘉实沪深300ETF联接A", "f_160706");
        // === 债券型基金 ===
        open.put("易方达稳健收益A", "f_110007");
        open.put("招商产业债A", "f_217022");
        open.put("广发趋势优选", "f_000215");
        open.put("富国信用债A", "f_000191");
        open.put("易方达裕丰回报", "f_000171");
        // === QDII 基金 ===
        open.put("易方达亚洲精选", "f_110001");
        open.put("工银全球股票", "f_486001");
        open.put("国泰纳斯达克100", "f_160213");
        open.put("博时标普500ETF联接A", "f_050025");
        open.put("博时标普500", "f_050025");
        open.put("华夏纳斯达克100", "f_015299");
        open.put("广发纳斯达克100", "f_270042");
        open.put("华安德国DAX", "f_000614");
        open.put("易方达中短期美元债A", "f_007360");
        // === 货币基金 ===
        open.put("余额宝", "f_000198");
        open.put("天弘余额宝", "f_000198");
        open.put("微信零钱通", "f_000198");
        open.put("南方现金增利", "f_202301");
        open.put("华夏现金增利", "f_003003");
        OPENEND_MAP = Map.copyOf(open);
    }

    public FundService() {
        this.restClient = RestClient.create();
    }

    /**
     * 查询基金行情。支持中文名称、带前缀代码、纯数字代码。
     */
    public String queryFund(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "错误: 请输入基金名称或代码\n"
                + "示例: 沪深300ETF / 易方达蓝筹精选 / 510300 / 005827";
        }

        String trimmed = input.trim();

        // 1. 名称映射查找（开放式基金优先，避免 ETF/基金重名）
        String fullCode = OPENEND_MAP.get(trimmed);
        String displayName = trimmed;
        String type = "openend";

        if (fullCode == null) {
            fullCode = ETF_MAP.get(trimmed);
            type = "etf";
        }
        if (fullCode == null) {
            fullCode = OPENEND_MAP.get(trimmed.toLowerCase());
            type = "openend";
        }
        if (fullCode == null) {
            fullCode = ETF_MAP.get(trimmed.toLowerCase());
            type = "etf";
        }

        if (fullCode != null) {
            return fetchWithCache(fullCode, displayName, type);
        }

        // 2. 已带 sh/sz 前缀 → ETF
        if (trimmed.matches("(?i)^(sh|sz)\\d{6}$")) {
            return fetchWithCache(trimmed.toLowerCase(), trimmed, "etf");
        }

        // 3. 已带 f_ 前缀 → 开放式基金
        if (trimmed.regionMatches(true, 0, "f_", 0, 2)) {
            return fetchWithCache(trimmed.toLowerCase(), trimmed, "openend");
        }

        // 4. 纯数字 6 位 → 开放式基金
        if (trimmed.matches("^\\d{6}$")) {
            return fetchWithCache("f_" + trimmed, trimmed, "openend");
        }

        // 5. 纯数字 5 位 → ETF
        if (trimmed.matches("^\\d{5}$")) {
            return fetchWithCache("sh" + trimmed, trimmed, "etf");
        }

        return "错误: 不支持的基金 '" + trimmed + "'\n"
            + "支持: 中文名称（沪深300ETF/易方达蓝筹精选）、代码（510300/005827）";
    }

    // ==================== 缓存 ====================

    private String fetchWithCache(String fullCode, String displayName, String type) {
        long now = System.currentTimeMillis();
        long[] ts = cacheTs.get(fullCode);
        if (ts != null && now - ts[0] < CACHE_TTL_MS) {
            String cached = cacheResult.get(fullCode);
            if (cached != null) return cached;
        }
        String result = fetchFundData(fullCode, displayName, type);
        cacheTs.put(fullCode, new long[]{now});
        cacheResult.put(fullCode, result);
        return result;
    }

    // ==================== 数据拉取 ====================

    private String fetchFundData(String fullCode, String displayName, String type) {
        log.info("查询基金: code={}, display={}, type={}", fullCode, displayName, type);

        if ("etf".equals(type)) {
            try {
                return fetchFromSinaStock(fullCode, displayName);
            } catch (Exception e) {
                log.warn("新浪ETF查询失败({}): {}", fullCode, e.getMessage());
                try { return fetchFromTencentStock(fullCode, displayName); }
                catch (Exception e2) { return "错误: ETF 查询失败"; }
            }
        } else {
            try {
                return fetchFromSinaFund(fullCode, displayName);
            } catch (Exception e) {
                log.warn("新浪基金查询失败({}): {}", fullCode, e.getMessage());
                try { return fetchFromTencentFund(fullCode, displayName); }
                catch (Exception e2) { return "错误: 基金查询失败"; }
            }
        }
    }

    // ==================== 新浪 - 开放式基金 ====================

    /**
     * 新浪基金接口 (f_ 前缀)。
     * 响应: var hq_str_f_005827="易方达蓝筹精选,1.506,1.506,1.4931,2026-07-27,133.669";
     * 字段: [0]名称 [1]单位净值 [2]累计净值 [3]前一日净值 [4]日期 [5]规模(亿)
     */
    private String fetchFromSinaFund(String fullCode, String displayName) {
        String response = restClient.get()
            .uri(SINA_API + fullCode)
            .header("Referer", "https://finance.sina.com.cn")
            .retrieve()
            .body(String.class);

        if (response == null || response.isBlank()) throw new RuntimeException("新浪返回空数据");
        log.info("新浪基金响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) throw new RuntimeException("响应格式异常");
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty()) throw new RuntimeException("基金代码无效或无数据");

        String[] fields = data.split(",", -1);
        if (fields.length < 5) throw new RuntimeException("字段不足: " + fields.length);

        String name = fields[0].trim();
        if (name.isEmpty()) name = displayName;
        String nav = fields[1];            // 单位净值
        String cumulativeNav = fields[2];  // 累计净值
        String prevNav = fields[3];        // 前一日净值
        String date = fields[4];           // 日期
        String size = fields.length > 5 ? fields[5] : ""; // 规模(亿)

        String fundCode = fullCode.startsWith("f_") ? fullCode.substring(2) : fullCode;
        String category = getFundCategory(fundCode, name);

        return formatOpenEndFund(name, fundCode, nav, cumulativeNav, prevNav, date, size, category);
    }

    // ==================== 新浪 - ETF ====================

    private String fetchFromSinaStock(String fullCode, String displayName) {
        String response = restClient.get()
            .uri(SINA_API + fullCode)
            .header("Referer", "https://finance.sina.com.cn")
            .retrieve()
            .body(String.class);

        if (response == null || response.isBlank()) throw new RuntimeException("新浪返回空数据");
        log.info("新浪ETF响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) throw new RuntimeException("响应格式异常");
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty()) throw new RuntimeException("ETF代码无效或无数据");

        String[] fields = data.split(",", -1);
        if (fields.length < 9) throw new RuntimeException("ETF字段不足: " + fields.length);

        String name = fields[0].trim();
        if (name.isEmpty()) name = displayName;
        String price = fields[3];
        String open = fields[1];
        String prevClose = fields[2];
        String high = fields[4];
        String low = fields[5];
        String volume = fields[8];
        String date = fields.length > 30 ? fields[30] : "";

        return formatEtfInfo(name, fullCode, price, prevClose, open, high, low, volume, date);
    }

    // ==================== 腾讯 - 开放式基金 ====================

    /**
     * 腾讯基金接口。代码格式: jj + 6位数字。
     * 响应: v_jj005827="005827~易方达蓝筹精选~0.0000~0.0000~~1.5060~1.5060~0.8640~2026-07-27~";
     * 字段: [0]代码 [1]名称 [2]日涨跌额 [3]日涨跌幅 [4]?? [5]单位净值 [6]累计净值 [7]前一日净值 [8]日期
     */
    private String fetchFromTencentFund(String fullCode, String displayName) {
        String code = fullCode.startsWith("f_") ? fullCode.substring(2) : fullCode;
        String response = restClient.get()
            .uri(TENCENT_API + "jj" + code)
            .retrieve()
            .body(String.class);

        if (response == null || response.isBlank()) throw new RuntimeException("腾讯返回空数据");
        log.info("腾讯基金响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) throw new RuntimeException("响应格式异常");
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty()) throw new RuntimeException("腾讯返回空数据");

        String[] fields = data.split("~", -1);
        if (fields.length < 8) throw new RuntimeException("腾讯字段不足: " + fields.length);

        String name = fields[1].trim();
        if (name.isEmpty()) name = displayName;
        String nav = fields[5];            // 单位净值
        String cumulativeNav = fields[6];  // 累计净值
        String prevNav = fields[7];        // 前一日净值
        String date = fields[8];           // 日期

        String fundCode = fullCode.startsWith("f_") ? fullCode.substring(2) : fullCode;
        String category = getFundCategory(fundCode, name);

        return formatOpenEndFund(name.isEmpty() ? displayName : name, fundCode, nav, cumulativeNav, prevNav, date, "", category);
    }

    // ==================== 腾讯 - ETF ====================

    private String fetchFromTencentStock(String fullCode, String displayName) {
        String response = restClient.get()
            .uri(TENCENT_API + fullCode)
            .retrieve()
            .body(String.class);

        if (response == null || response.isBlank()) throw new RuntimeException("腾讯返回空数据");
        log.info("腾讯ETF响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) throw new RuntimeException("响应格式异常");
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty()) throw new RuntimeException("腾讯返回空数据");

        String[] fields = data.split("~", -1);
        if (fields.length < 8) throw new RuntimeException("腾讯字段不足: " + fields.length);

        String name = fields[1].trim();
        String price = fields[3];
        String prevClose = fields[4];
        String open = fields[5];
        String volume = fields[6];

        return formatEtfInfo(name.isEmpty() ? displayName : name, fullCode, price, prevClose, open, "—", "—", volume, "");
    }

    // ==================== 基金类型识别 ====================

    private String getFundCategory(String code, String name) {
        String lower = name.toLowerCase();
        if (lower.contains("货币") || lower.contains("余额宝") || lower.contains("零钱通")
            || "000198".equals(code) || "202301".equals(code) || "003003".equals(code)) {
            return "货币型";
        }
        if (lower.contains("qdii") || lower.contains("纳斯达克") || lower.contains("标普")
            || lower.contains("全球") || lower.contains("亚洲") || lower.contains("德国")
            || lower.contains("美元") || lower.contains("越南") || lower.contains("印度")) {
            return "QDII";
        }
        if (lower.contains("债") || lower.contains("信用") || lower.contains("裕丰")
            || lower.contains("稳健") || lower.contains("短债") || lower.contains("中短")) {
            return "债券型";
        }
        if (lower.contains("指数") || lower.contains("联接") || lower.contains("ETF联接")
            || lower.contains("沪深300") || lower.contains("中证500") || lower.contains("上证50")
            || lower.contains("创业板") || lower.contains("科创") || lower.contains("lof")) {
            return "指数型";
        }
        if (lower.contains("混合") || lower.contains("灵活配置") || lower.contains("平衡")) {
            return "混合型";
        }
        // 根据代码段推断
        if (code.startsWith("16")) return "指数型(LOF)";
        if (code.startsWith("00") || code.startsWith("01") || code.startsWith("02") || code.startsWith("05")) {
            return "混合型"; // 大部分 0/1/5 开头是混合型
        }
        if (code.startsWith("11")) return "混合型";
        if (code.startsWith("21")) return "债券型";
        if (code.startsWith("48") || code.startsWith("51")) return "QDII";
        return "基金";
    }

    // ==================== 格式化输出 ====================

    private String formatOpenEndFund(String name, String code, String nav,
                                      String cumulativeNav, String prevNav, String date,
                                      String size, String category) {
        try {
            BigDecimal navVal = new BigDecimal(nav.trim());
            String direction = "📈";
            String sign = "";

            if (prevNav != null && !prevNav.isBlank()) {
                BigDecimal prev = new BigDecimal(prevNav.trim());
                if (prev.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = navVal.subtract(prev);
                    direction = change.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                    sign = change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(direction).append(" ").append(name).append("（").append(code).append("）\n");
            sb.append("  类型: 开放式基金 · ").append(category).append("\n");
            sb.append("  单位净值: ").append(nav).append("\n");

            if (cumulativeNav != null && !cumulativeNav.isBlank()
                && !"0".equals(cumulativeNav.trim()) && !"0.0000".equals(cumulativeNav.trim())) {
                sb.append("  累计净值: ").append(cumulativeNav).append("\n");
            }

            if (prevNav != null && !prevNav.isBlank()) {
                BigDecimal prev = new BigDecimal(prevNav.trim());
                if (prev.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = navVal.subtract(prev);
                    BigDecimal changePct = change.divide(prev, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                    sb.append("  日涨跌: ").append(sign).append(change.setScale(4, RoundingMode.HALF_UP)).append("\n");
                    sb.append("  日涨跌幅: ").append(sign).append(changePct.setScale(2, RoundingMode.HALF_UP)).append("%\n");
                }
            }

            if (size != null && !size.isBlank() && !"0".equals(size.trim())) {
                try {
                    BigDecimal sizeVal = new BigDecimal(size.trim());
                    if (sizeVal.compareTo(new BigDecimal("10000")) >= 0) {
                        sb.append("  规模: ").append(sizeVal.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP)).append("万亿\n");
                    } else {
                        sb.append("  规模: ").append(size).append("亿\n");
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (date != null && !date.isBlank()) {
                sb.append("  日期: ").append(date.trim());
            }
            return sb.toString();
        } catch (NumberFormatException e) {
            log.warn("基金净值格式化失败: nav={}", nav);
            return name + "（" + code + "）\n  单位净值: " + nav;
        }
    }

    private String formatEtfInfo(String name, String code, String currentPrice,
                                  String prevClose, String open, String high,
                                  String low, String volume, String date) {
        try {
            BigDecimal price = new BigDecimal(currentPrice.trim());
            BigDecimal change = BigDecimal.ZERO;
            BigDecimal changePct = BigDecimal.ZERO;
            String direction = "📈";
            String sign = "";

            if (prevClose != null && !prevClose.isBlank()) {
                BigDecimal prev = new BigDecimal(prevClose.trim());
                if (prev.compareTo(BigDecimal.ZERO) > 0) {
                    change = price.subtract(prev);
                    changePct = change.divide(prev, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                    direction = change.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                    sign = change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(direction).append(" ").append(name).append("（").append(code).append("）\n");
            sb.append("  类型: 场内基金 · ETF\n");
            sb.append("  当前价: ").append(price).append("\n");
            sb.append("  涨跌额: ").append(sign).append(change.setScale(3, RoundingMode.HALF_UP)).append("\n");
            sb.append("  涨跌幅: ").append(sign).append(changePct.setScale(2, RoundingMode.HALF_UP)).append("%\n");
            if (isNonZero(open)) sb.append("  今开: ").append(open.trim()).append("\n");
            if (isNonZero(high)) sb.append("  最高: ").append(high.trim()).append("\n");
            if (isNonZero(low)) sb.append("  最低: ").append(low.trim()).append("\n");
            if (isNonZero(volume)) sb.append("  成交量: ").append(formatVolume(volume.trim())).append("\n");
            if (date != null && !date.isBlank()) sb.append("  日期: ").append(date.trim());
            return sb.toString();
        } catch (NumberFormatException e) {
            log.warn("ETF价格格式化失败: price={}", currentPrice);
            return name + "（" + code + "）\n  当前价: " + currentPrice;
        }
    }

    private boolean isNonZero(String val) {
        if (val == null || val.isBlank()) return false;
        String s = val.trim();
        return !"0".equals(s) && !"0.000".equals(s) && !"0.00".equals(s);
    }

    private String formatVolume(String volume) {
        try {
            long vol = Long.parseLong(volume);
            if (vol >= 10000_0000) return String.format("%.2f亿手", vol / 10000_0000.0);
            if (vol >= 10000) return String.format("%.1f万手", vol / 10000.0);
            return vol + "手";
        } catch (NumberFormatException e) {
            return volume + "手";
        }
    }

    // ==================== 历史净值（东方财富） ====================

    /**
     * 查询基金历史净值，返回按日期正序排列的数据列表。
     *
     * 获取基金历史净值（带缓存）。
     */
    public java.util.List<NavData> getHistoricalNav(String input, int days) {
        String code = resolveFundCode(input);
        if (code == null) return java.util.List.of();

        // 检查缓存
        String cacheKey = code + ":" + days;
        long now = System.currentTimeMillis();
        long[] ts = navCacheTs.get(cacheKey);
        if (ts != null && now - ts[0] < NAV_CACHE_TTL_MS) {
            List<NavData> cached = navCacheData.get(cacheKey);
            if (cached != null) {
                log.debug("基金净值缓存命中: key={}", cacheKey);
                return cached;
            }
        }

        String eastmoneyCode = code.startsWith("f_") ? code.substring(2) : code;
        int pageSize = Math.min(days + 10, 60);

        try {
            String url = "https://api.fund.eastmoney.com/f10/lsjz?fundCode=" + eastmoneyCode
                + "&pageIndex=1&pageSize=" + pageSize;
            String response = restClient.get()
                .uri(url)
                .header("Referer", "https://fund.eastmoney.com")
                .retrieve()
                .body(String.class);

            if (response == null || response.isBlank()) return java.util.List.of();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);

            int errCode = root.path("ErrCode").asInt(-1);
            if (errCode != 0) {
                log.warn("东方财富历史净值查询失败: code={}, errCode={}", eastmoneyCode, errCode);
                return java.util.List.of();
            }

            com.fasterxml.jackson.databind.JsonNode list = root.path("Data").path("LSJZList");
            java.util.List<NavData> result = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : list) {
                String date = item.path("FSRQ").asText("");
                String nav = item.path("DWJZ").asText("");
                String changePct = item.path("JZZZL").asText("");
                if (!date.isEmpty() && !nav.isEmpty()) {
                    result.add(new NavData(date, nav, changePct));
                }
            }

            // API 返回倒序，反转为正序（日期从旧到新）
            java.util.Collections.reverse(result);

            // 只取最近 days 条
            if (result.size() > days) {
                result = result.subList(result.size() - days, result.size());
            }

            // 写入缓存
            if (!result.isEmpty()) {
                navCacheTs.put(cacheKey, new long[]{now});
                navCacheData.put(cacheKey, result);
            }

            return result;
        } catch (Exception e) {
            log.warn("东方财富历史净值查询异常: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    /**
     * 解析基金代码（复用 FundTool 的逻辑）
     */
    private String resolveFundCode(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();

        // 名称查找
        String code = OPENEND_MAP.get(trimmed);
        if (code != null) return code;
        code = ETF_MAP.get(trimmed);
        if (code != null) return code;
        code = OPENEND_MAP.get(trimmed.toLowerCase());
        if (code != null) return code;
        code = ETF_MAP.get(trimmed.toLowerCase());
        if (code != null) return code;

        // 已带前缀
        if (trimmed.matches("(?i)^(sh|sz)\\d{6}$")) return trimmed.toLowerCase();
        if (trimmed.regionMatches(true, 0, "f_", 0, 2)) return trimmed.toLowerCase();

        // 纯数字
        if (trimmed.matches("^\\d{6}$")) return "f_" + trimmed;
        if (trimmed.matches("^\\d{5}$")) return "sh" + trimmed;

        return null;
    }

    /** 历史净值数据 */
    public record NavData(String date, String nav, String changePct) {}
}
