package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 股票查询服务，接入新浪/腾讯公开接口。
 * <p>
 * 支持 A 股（上证/深证/北交所）、港股、美股的实时行情查询。
 * 优先使用新浪接口，失败时自动回退到腾讯接口。
 * <p>
 * 内部代码格式统一为新浪格式：
 *   A 股: sh600519, sz000001, bj830799
 *   港股: hk00700, hk09988
 *   美股: gb_aapl, gb_tsla
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private static final String SINA_API = "http://hq.sinajs.cn/list=";
    private static final String TENCENT_API = "http://qt.gtimg.cn/q=";
    private static final long KLINE_CACHE_TTL_MS = 300_000; // 5分钟缓存

    private final RestClient restClient;

    // K线数据缓存: code+days -> [timestamp, result]
    private final ConcurrentHashMap<String, long[]> klineCacheTs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<KlineData>> klineCacheData = new ConcurrentHashMap<>();

    /** 常用股票名称→代码映射（代码为新浪格式） */
    private static final Map<String, String> STOCK_NAME_MAP;

    static {
        Map<String, String> map = new java.util.HashMap<>();
        // === A 股大盘指数 ===
        map.put("上证指数", "sh000001");
        map.put("深证成指", "sz399001");
        map.put("创业板指", "sz399006");
        map.put("科创50", "sh000688");
        map.put("沪深300", "sh000300");
        // === A 股热门 ===
        map.put("贵州茅台", "sh600519");
        map.put("茅台", "sh600519");
        map.put("宁德时代", "sz300750");
        map.put("比亚迪", "sz002594");
        map.put("中国平安", "sh601318");
        map.put("招商银行", "sh600036");
        map.put("五粮液", "sz000858");
        map.put("隆基绿能", "sh601012");
        map.put("药明康德", "sh603259");
        map.put("中芯国际", "sh688981");
        map.put("工商银行", "sh601398");
        map.put("农业银行", "sh601288");
        map.put("中国石油", "sh601857");
        map.put("中国移动", "sh600941");
        map.put("长江电力", "sh600900");
        map.put("海康威视", "sz002415");
        map.put("格力电器", "sz000651");
        map.put("美的集团", "sz000333");
        map.put("万科", "sz000002");
        map.put("保利发展", "sh600048");
        map.put("中兴通讯", "sz000063");
        map.put("寒武纪", "sh688256");
        // === 港股热门 ===
        map.put("腾讯控股", "hk00700");
        map.put("腾讯", "hk00700");
        map.put("阿里巴巴", "hk09988");
        map.put("阿里", "hk09988");
        map.put("美团", "hk03690");
        map.put("京东", "hk09618");
        map.put("百度", "hk09888");
        map.put("快手", "hk01024");
        map.put("小米", "hk01810");
        map.put("网易", "hk09999");
        map.put("比亚迪股份", "hk01211");
        map.put("理想汽车", "hk02015");
        map.put("小鹏汽车", "hk09868");
        map.put("中国移动港股", "hk00941");
        map.put("友邦保险", "hk01299");
        map.put("港交所", "hk00388");
        map.put("汇丰控股", "hk00005");
        map.put("海底捞", "hk06862");
        map.put("泡泡玛特", "hk09992");
        // === 美股热门 ===
        map.put("苹果", "gb_aapl");
        map.put("微软", "gb_msft");
        map.put("谷歌", "gb_goog");
        map.put("亚马逊", "gb_amzn");
        map.put("英伟达", "gb_nvda");
        map.put("特斯拉", "gb_tsla");
        map.put("meta", "gb_meta");
        map.put("奈飞", "gb_nflx");
        map.put("台积电", "gb_tsm");
        map.put("拼多多", "gb_pdd");
        map.put("伯克希尔", "gb_brk.b");
        STOCK_NAME_MAP = Map.copyOf(map);
    }

    public StockService() {
        this.restClient = RestClient.create();
    }

    /**
     * 查询股票实时行情。支持股票名称、纯数字代码、字母代码、带前缀代码。
     *
     * @param input 股票名称或代码，如 "贵州茅台"、"600519"、"sh600519"、"腾讯"、"AAPL"、"gb_aapl"
     * @return 格式化的行情字符串
     */
    public String queryStock(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "错误: 请输入股票名称或代码\n"
                + "示例: 贵州茅台 / 600519 / sh600519 / 腾讯 / AAPL";
        }

        String trimmed = input.trim();

        // 1. 先从名称映射查找
        String fullCode = STOCK_NAME_MAP.get(trimmed);
        if (fullCode == null) {
            fullCode = STOCK_NAME_MAP.get(trimmed.toLowerCase());
        }
        if (fullCode != null) {
            return fetchStockData(fullCode, trimmed);
        }

        // 2. 已带新浪前缀（sh/sz/bj/hk/gb_）
        if (trimmed.matches("(?i)^(?:sh|sz|bj|hk|gb_)[a-z0-9.]+$")) {
            return fetchStockData(trimmed.toLowerCase(), trimmed);
        }

        // 3. 纯数字代码，自动识别交易所
        if (trimmed.matches("^\\d{5,6}$")) {
            String prefix = detectNumericMarket(trimmed);
            if (prefix == null) {
                return "错误: 无法识别股票代码 '" + trimmed + "' 的交易所\n"
                    + "A 股 6 位代码（如 600519），港股 5 位代码（如 00700）";
            }
            return fetchStockData(prefix + trimmed, trimmed);
        }

        // 4. 纯字母 → 美股（自动补 gb_ 前缀）
        if (trimmed.matches("(?i)^[a-z]+$")) {
            String usCode = "gb_" + trimmed.toLowerCase();
            return fetchStockData(usCode, trimmed);
        }

        // 5. 带 . 的（如 BRK.B）→ 美股
        if (trimmed.matches("(?i)^[a-z]+(\\.[a-z]+)?$")) {
            String usCode = "gb_" + trimmed.toLowerCase();
            return fetchStockData(usCode, trimmed);
        }

        return "错误: 不支持的股票 '" + trimmed + "'\n"
            + "支持的格式: 股票名称（贵州茅台/腾讯/苹果）、代码（600519/00700/AAPL）、"
            + "带前缀（sh600519/hk00700/gb_aapl）";
    }

    /**
     * 根据纯数字代码判断所属交易所。
     * A 股 6 位: 6xxxxx→上证, 0xxxxx/3xxxxx→深证, 4xxxxx/8xxxxx→北交所
     * 港股 5 位: 0xxxx→港股（如 00700）
     */
    private String detectNumericMarket(String code) {
        if (code.length() == 5) {
            return "hk";  // 5 位数字 → 港股
        }
        // 6 位数字 → A 股
        char first = code.charAt(0);
        return switch (first) {
            case '6', '5' -> "sh";
            case '0', '3', '1' -> "sz";
            case '8', '4' -> "bj";
            default -> null;
        };
    }

    /**
     * 解析内部代码，提取市场类型。
     */
    private String getMarketType(String fullCode) {
        if (fullCode.startsWith("gb_")) return "us";
        if (fullCode.startsWith("hk")) return "hk";
        return "a";
    }

    // ==================== 拉取数据 ====================

    /**
     * 先尝试新浪，失败回退腾讯。
     */
    private String fetchStockData(String fullCode, String displayName) {
        log.info("查询股票: code={}, display={}", fullCode, displayName);

        try {
            return fetchFromSina(fullCode);
        } catch (Exception e) {
            log.warn("新浪接口查询失败: {}, 回退到腾讯接口", e.getMessage());
            try {
                return fetchFromTencent(fullCode);
            } catch (Exception e2) {
                log.error("腾讯接口也查询失败: {}", e2.getMessage());
                return "错误: 股票查询失败，两个接口均不可用 - " + e2.getMessage();
            }
        }
    }

    // ==================== 新浪接口 ====================

    /**
     * 新浪股票接口。根据市场类型分别解析。
     */
    private String fetchFromSina(String fullCode) {
        String response = restClient.get()
                .uri(SINA_API + fullCode)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RuntimeException("新浪返回空数据");
        }

        log.info("新浪响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) {
            throw new RuntimeException("新浪响应格式异常: 无引号包裹");
        }
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty()) {
            throw new RuntimeException("新浪返回空行情数据，股票代码可能无效或无数据");
        }

        String[] fields = data.split(",");
        String market = getMarketType(fullCode);

        return switch (market) {
            case "hk" -> parseSinaHK(fullCode, fields);
            case "us" -> parseSinaUS(fullCode, fields);
            default -> parseSinaA(fullCode, fields);
        };
    }

    /**
     * 解析新浪 A 股响应。
     * 字段: 名称,今开,昨收,当前价,最高,最低,买价,卖价,成交量,成交额,...
     */
    private String parseSinaA(String code, String[] fields) {
        if (fields.length < 9) {
            throw new RuntimeException("A股字段不足: " + fields.length);
        }
        String name = fields[0];
        String open = fields[1];
        String prevClose = fields[2];
        String price = fields[3];
        String high = fields[4];
        String low = fields[5];
        String volume = fields.length > 8 ? fields[8] : "0";
        return formatStockInfo(name, code, price, open, prevClose, high, low, volume);
    }

    /**
     * 解析新浪港股响应。
     * 字段: 英文名,今开,昨收,当前价,最高,最低,买价,卖价,成交量,成交额,日期,时间,...
     */
    private String parseSinaHK(String code, String[] fields) {
        if (fields.length < 9) {
            throw new RuntimeException("港股字段不足: " + fields.length);
        }
        String engName = fields[0];
        String open = fields[1];
        String prevClose = fields[2];
        String price = fields[3];
        String high = fields[4];
        String low = fields[5];
        String volume = fields.length > 8 ? fields[8] : "0";
        return formatStockInfo(engName, code, price, open, prevClose, high, low, volume);
    }

    /**
     * 解析新浪美股响应。
     * 格式与 A 股/港股不同，字段位置有偏移。
     * 字段: 名称,当前价,涨跌幅(%),涨跌额,...,买价,卖价,最低,最高,成交量,成交额,市值,市盈率,...,日期,时间
     * <p>
     * 索引参考（以实际返回为准）:
     *   0: 名称
     *   1: 当前价
     *   2: 涨跌幅(%)
     *   3: 涨跌额
     *   4-20: 其他（买卖盘等）
     *   21: 最低 (approx)
     *   22: 最高 (approx)
     *   ...末尾前两个: 日期, 时间
     */
    private String parseSinaUS(String code, String[] fields) {
        if (fields.length < 5) {
            throw new RuntimeException("美股字段不足: " + fields.length);
        }
        String name = fields[0];
        String price = fields[1];
        String changePctStr = fields.length > 2 ? fields[2] : "0";
        String volume = "0";

        // 尝试从后面字段提取成交量和最高最低
        // 新浪美股格式较不稳定，尽量提取有用信息
        String prevClose = "";
        String open = "—";
        String high = "—";
        String low = "—";

        // 从末尾找日期时间，再往前找价量
        // 常见结构: ..., 买价, 卖价, 日低, 日高, 成交量, 成交额, 市值, 市盈率, 日期, 时间
        int len = fields.length;
        if (len >= 25) {
            // 倒数第6个左右通常是成交量
            volume = fields[len - 6];
            high = fields[len - 8];
            low = fields[len - 9];
        }

        // 计算昨收: 当前价 - 涨跌额 = 昨收（近似）
        try {
            BigDecimal curPrice = new BigDecimal(price);
            String changeStr = fields.length > 3 ? fields[3] : "0";
            BigDecimal change = new BigDecimal(changeStr);
            BigDecimal prev = curPrice.subtract(change);
            prevClose = prev.toPlainString();
        } catch (NumberFormatException e) {
            prevClose = "—";
        }

        // 美股用 $ 符号
        return formatUSStockInfo(name, code, price, prevClose, open, high, low, volume, changePctStr);
    }

    // ==================== 腾讯接口（回退） ====================

    /**
     * 腾讯股票接口。需要对美股代码做转换: gb_aapl → usAAPL
     */
    private String fetchFromTencent(String fullCode) {
        String tencentCode = toTencentCode(fullCode);

        String response = restClient.get()
                .uri(TENCENT_API + tencentCode)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RuntimeException("腾讯返回空数据");
        }

        log.info("腾讯响应(code={}): {}", fullCode, response);

        int eqIdx = response.indexOf('"');
        if (eqIdx == -1) {
            throw new RuntimeException("腾讯响应格式异常");
        }
        String data = response.substring(eqIdx + 1, response.lastIndexOf('"'));
        if (data.isEmpty()) {
            throw new RuntimeException("腾讯返回空行情数据");
        }

        String[] fields = data.split("~");
        if (fields.length < 8) {
            throw new RuntimeException("腾讯返回字段不足: " + fields.length);
        }

        String name = fields[1];
        String price = fields[3];
        String prevClose = fields[4];
        String open = fields[5];
        String volume = fields[6];
        String high = "—";
        String low = "—";

        String market = getMarketType(fullCode);
        if ("us".equals(market)) {
            return formatUSStockInfo(name, fullCode, price, prevClose, open, high, low, volume, null);
        }
        return formatStockInfo(name, fullCode, price, open, prevClose, high, low, volume);
    }

    /**
     * 将新浪格式代码转为腾讯格式。
     * gb_aapl → usAAPL, hk00700 → hk00700, sh600519 → sh600519
     */
    private String toTencentCode(String sinaCode) {
        if (sinaCode.startsWith("gb_")) {
            return "us" + sinaCode.substring(3).toUpperCase();
        }
        return sinaCode;
    }

    // ==================== 格式化输出 ====================

    /**
     * 格式化 A 股/港股行情。
     */
    private String formatStockInfo(String name, String code, String currentPrice,
                                    String open, String yesterdayClose, String high,
                                    String low, String volume) {
        try {
            BigDecimal price = new BigDecimal(currentPrice);
            BigDecimal prevClose = new BigDecimal(yesterdayClose);

            BigDecimal change = price.subtract(prevClose);
            BigDecimal changePct = BigDecimal.ZERO;
            if (prevClose.compareTo(BigDecimal.ZERO) > 0) {
                changePct = change.divide(prevClose, 2, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            String direction = change.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
            String sign = change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

            String marketLabel = getMarketLabel(code);

            StringBuilder sb = new StringBuilder();
            sb.append(name).append("（").append(code).append("）").append(direction).append("\n");
            sb.append("  市场: ").append(marketLabel).append("\n");
            sb.append("  当前价: ").append(price).append("\n");
            sb.append("  涨跌额: ").append(sign).append(change).append("\n");
            sb.append("  涨跌幅: ").append(sign).append(changePct).append("%\n");
            sb.append("  今开: ").append(open).append("\n");
            sb.append("  昨收: ").append(yesterdayClose).append("\n");
            if (!high.isBlank() && !"—".equals(high)) {
                sb.append("  最高: ").append(high).append("\n");
            }
            if (!low.isBlank() && !"—".equals(low)) {
                sb.append("  最低: ").append(low).append("\n");
            }
            sb.append("  成交量: ").append(formatVolume(volume));
            return sb.toString();
        } catch (NumberFormatException e) {
            log.warn("价格格式化失败: price={}, prevClose={}", currentPrice, yesterdayClose);
            return name + "（" + code + "）\n  当前价: " + currentPrice
                    + "\n  今开: " + open + "\n  昨收: " + yesterdayClose;
        }
    }

    /**
     * 格式化美股行情（价格以美元计）。
     */
    private String formatUSStockInfo(String name, String code, String currentPrice,
                                      String yesterdayClose, String open, String high,
                                      String low, String volume, String changePctStr) {
        try {
            BigDecimal price = new BigDecimal(currentPrice);

            String direction = "📈";
            String changeStr = "—";
            String sign = "";

            if (!"—".equals(yesterdayClose) && !yesterdayClose.isBlank()) {
                BigDecimal prevClose = new BigDecimal(yesterdayClose);
                BigDecimal change = price.subtract(prevClose);
                direction = change.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                sign = change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                changeStr = sign + change.toPlainString();
            } else if (changePctStr != null && !changePctStr.isBlank() && !"0.00".equals(changePctStr)) {
                // 从涨跌幅推断涨跌方向
                try {
                    double pct = Double.parseDouble(changePctStr);
                    direction = pct >= 0 ? "📈" : "📉";
                    sign = pct >= 0 ? "+" : "";
                } catch (NumberFormatException ignored) {}
            }

            StringBuilder sb = new StringBuilder();
            sb.append(name).append("（").append(code).append("）").append(direction).append("\n");
            sb.append("  市场: 美股\n");
            sb.append("  当前价: $").append(price).append("\n");
            if (!"—".equals(yesterdayClose) && !yesterdayClose.isBlank()) {
                sb.append("  昨收: $").append(yesterdayClose).append("\n");
            }
            if (changePctStr != null && !changePctStr.isBlank()) {
                sb.append("  涨跌幅: ").append(sign).append(changePctStr).append("%\n");
            } else if (!"—".equals(changeStr)) {
                sb.append("  涨跌额: $").append(changeStr).append("\n");
            }
            if (!"—".equals(open) && !open.isBlank()) {
                sb.append("  今开: $").append(open).append("\n");
            }
            if (!"—".equals(high) && !high.isBlank()) {
                sb.append("  最高: $").append(high).append("\n");
            }
            if (!"—".equals(low) && !low.isBlank()) {
                sb.append("  最低: $").append(low).append("\n");
            }
            sb.append("  成交量: ").append(formatVolume(volume));
            return sb.toString();
        } catch (NumberFormatException e) {
            log.warn("美股价格格式化失败: price={}", currentPrice);
            return name + "（" + code + "）\n  当前价: $" + currentPrice;
        }
    }

    /**
     * 格式化成交量。
     */
    private String formatVolume(String volume) {
        try {
            long vol = Long.parseLong(volume);
            if (vol >= 10000) {
                return String.format("%.1f万手", vol / 10000.0);
            }
            return vol + "手";
        } catch (NumberFormatException e) {
            return volume + "手";
        }
    }

    /**
     * 根据代码前缀返回市场名称标签。
     */
    private String getMarketLabel(String code) {
        if (code.startsWith("sh")) return "上证 A 股";
        if (code.startsWith("sz")) return "深证 A 股";
        if (code.startsWith("bj")) return "北交所";
        if (code.startsWith("hk")) return "港股";
        if (code.startsWith("gb_")) return "美股";
        return "A 股";
    }

    // ==================== 历史K线 ====================

    /** K线数据记录 */
    public record KlineData(String date, String open, String high, String low, String close, String volume) {}

    // ==================== 基本面数据 ====================

    /** 股票基本面数据 */
    public record FundamentalData(
        String pe,           // 市盈率
        String peRank,       // 市盈率排名百分位
        String pb,           // 市净率
        String marketCap,    // 总市值
        String circulatingCap, // 流通市值
        String dividendYield, // 股息率
        String roe,          // 净资产收益率
        String revenue,      // 营收
        String netProfit     // 净利润
    ) {}

    /**
     * 获取股票基本面数据（东方财富接口）。
     *
     * @param input 股票名称或代码
     * @return 基本面数据，查询失败返回null
     */
    public FundamentalData getFundamentals(String input) {
        String code = resolveStockCode(input);
        if (code == null) return null;

        // 东方财富 secid 格式: 1.600519(上证), 0.000001(深证)
        String secid = toEastMoneySecid(code);
        if (secid == null) return null;

        try {
            String urlStr = "http://push2.eastmoney.com/api/qt/stock/get?secid=" + secid
                + "&fields=f57,f58,f162,f163,f164,f167,f116,f117&_=" + System.currentTimeMillis();

            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Referer", "https://quote.eastmoney.com");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            java.io.InputStream is = conn.getInputStream();
            String response = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            is.close();
            conn.disconnect();

            if (response == null || response.isBlank()) return null;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);
            com.fasterxml.jackson.databind.JsonNode data = root.path("data");

            if (data.isMissingNode()) return null;

            String pe = formatFundamentalValue(data.path("f162").asText(""), 2);
            String pb = formatFundamentalValue(data.path("f167").asText(""), 2);
            String marketCap = formatMarketCap(data.path("f116").asText(""));
            String circulatingCap = formatMarketCap(data.path("f117").asText(""));

            return new FundamentalData(pe, "", pb, marketCap, circulatingCap, "—", "—", "", "");
        } catch (Exception e) {
            log.warn("基本面数据查询失败: code={}, error={}", code, e.getMessage());
            return null;
        }
    }

    /**
     * 将新浪格式代码转为东方财富 secid。
     */
    private String toEastMoneySecid(String sinaCode) {
        if (sinaCode.startsWith("sh")) {
            return "1." + sinaCode.substring(2);
        } else if (sinaCode.startsWith("sz")) {
            return "0." + sinaCode.substring(2);
        } else if (sinaCode.startsWith("bj")) {
            return "0." + sinaCode.substring(2);
        }
        return null;
    }

    private String formatFundamentalValue(String val, int scale) {
        if (val == null || val.isEmpty() || "null".equals(val) || "-".equals(val)) return "—";
        try {
            BigDecimal bd = new BigDecimal(val);
            // 东方财富返回的是放大100倍的整数
            bd = bd.divide(new BigDecimal("100"), scale, RoundingMode.HALF_UP);
            return bd.toString();
        } catch (NumberFormatException e) {
            return val;
        }
    }

    private String formatMarketCap(String val) {
        if (val == null || val.isEmpty() || "null".equals(val) || "-".equals(val)) return "—";
        try {
            BigDecimal bd = new BigDecimal(val);
            BigDecimal yi = bd.divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP);
            if (yi.compareTo(new BigDecimal("10000")) >= 0) {
                return yi.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP) + "万亿";
            }
            return yi + "亿";
        } catch (NumberFormatException e) {
            return val;
        }
    }

    /**
     * 获取股票历史K线数据（带缓存）。
     * A股使用新浪API，港股/美股使用腾讯API。
     */
    public List<KlineData> getHistoricalKline(String input, int days) {
        String code = resolveStockCode(input);
        if (code == null) return List.of();

        // 检查缓存
        String cacheKey = code + ":" + days;
        long now = System.currentTimeMillis();
        long[] ts = klineCacheTs.get(cacheKey);
        if (ts != null && now - ts[0] < KLINE_CACHE_TTL_MS) {
            List<KlineData> cached = klineCacheData.get(cacheKey);
            if (cached != null) {
                log.debug("K线缓存命中: key={}", cacheKey);
                return cached;
            }
        }

        // 获取数据
        List<KlineData> result;
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            result = getAStockKline(code, days);
        } else if (code.startsWith("hk")) {
            result = getHKStockKline(code, days);
        } else if (code.startsWith("gb_")) {
            result = getUSStockKline(code, days);
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

    /** A股K线（新浪API） */
    private List<KlineData> getAStockKline(String code, int days) {
        String url = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20_"
            + code + "=/CN_MarketDataService.getKLineData?symbol=" + code
            + "&scale=240&ma=no&datalen=" + (days + 20);

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
                String date = item.path("day").asText("");
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
                result = result.subList(result.size() - days, result.size());
            }

            log.info("A股K线获取成功: code={}, 数据量={}", code, result.size());
            return result;

        } catch (Exception e) {
            log.error("A股K线获取失败: code={}", code, e);
            return List.of();
        }
    }

    /** 港股K线（腾讯API） */
    private List<KlineData> getHKStockKline(String code, int days) {
        String url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param="
            + code + ",day,,," + (days + 20) + ",qfq";

        try {
            String response = restClient.get()
                .uri(url)
                .header("Referer", "https://web.ifzq.gtimg.cn")
                .retrieve()
                .body(String.class);

            if (response == null || response.isBlank()) return List.of();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);

            // data.hk00700.day = [[date, open, close, high, low, volume], ...]
            com.fasterxml.jackson.databind.JsonNode dataNode = root.path("data").path(code).path("day");
            if (dataNode.isMissingNode() || !dataNode.isArray()) {
                // 尝试 qfqday 字段
                dataNode = root.path("data").path(code).path("qfqday");
            }
            if (dataNode.isMissingNode() || !dataNode.isArray()) return List.of();

            List<KlineData> result = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : dataNode) {
                if (!item.isArray() || item.size() < 6) continue;
                String date = item.get(0).asText("");
                String open = item.get(1).asText("");
                String close = item.get(2).asText("");
                String high = item.get(3).asText("");
                String low = item.get(4).asText("");
                String volume = item.get(5).asText("0");
                // 去掉 volume 中的小数点
                if (volume.contains(".")) volume = volume.substring(0, volume.indexOf('.'));
                if (!date.isEmpty() && !close.isEmpty()) {
                    result.add(new KlineData(date, open, high, low, close, volume));
                }
            }

            if (result.size() > days) {
                result = result.subList(result.size() - days, result.size());
            }

            log.info("港股K线获取成功: code={}, 数据量={}", code, result.size());
            return result;

        } catch (Exception e) {
            log.error("港股K线获取失败: code={}", code, e);
            return List.of();
        }
    }

    /** 美股K线（腾讯API） */
    /**
     * 美股K线（新浪 US_MinKService API）。
     * 返回 JSONP: var _usXXX=([{d,o,h,l,c,v,a},...])
     * 数据从 1984 年至今，远多于腾讯 API。
     */
    private List<KlineData> getUSStockKline(String code, int days) {
        String symbol = code.substring(3).toUpperCase(); // gb_aapl -> AAPL
        String url = "https://stock.finance.sina.com.cn/usstock/api/jsonp.php/var%20_"
            + code + "=/US_MinKService.getDailyK?symbol=" + symbol
            + "&datalen=" + (days + 20);

        try {
            String response = restClient.get()
                .uri(url)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve()
                .body(String.class);

            if (response == null || response.isBlank()) return List.of();

            // 解析 JSONP: var _gb_aapl=([{...}])
            int start = response.indexOf("([");
            int end = response.lastIndexOf("])");
            if (start < 0 || end < 0) {
                log.warn("美股K线JSONP解析失败: {}", response.substring(0, Math.min(200, response.length())));
                return List.of();
            }
            String json = response.substring(start + 1, end + 1);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);

            List<KlineData> result = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : root) {
                String date = item.path("d").asText("");
                String open = item.path("o").asText("");
                String high = item.path("h").asText("");
                String low = item.path("l").asText("");
                String close = item.path("c").asText("");
                String volume = item.path("v").asText("0");
                if (!date.isEmpty() && !close.isEmpty()) {
                    result.add(new KlineData(date, open, high, low, close, volume));
                }
            }

            if (result.size() > days) {
                result = result.subList(result.size() - days, result.size());
            }

            log.info("美股K线获取成功(Sina): symbol={}, 数据量={}", symbol, result.size());
            return result;

        } catch (Exception e) {
            log.error("美股K线获取失败: code={}", code, e);
            return List.of();
        }
    }

    /**
     * 解析股票代码（返回新浪格式完整代码）。
     */
    private String resolveStockCode(String input) {
        if (input == null || input.isBlank()) return null;

        String trimmed = input.trim();

        // 1. 先从名称映射查找
        String fullCode = STOCK_NAME_MAP.get(trimmed);
        if (fullCode == null) {
            fullCode = STOCK_NAME_MAP.get(trimmed.toLowerCase());
        }
        if (fullCode != null) return fullCode;

        // 2. 已带新浪前缀
        if (trimmed.matches("(?i)^(?:sh|sz|bj|hk|gb_)[a-z0-9.]+$")) {
            return trimmed.toLowerCase();
        }

        // 3. 纯数字代码
        if (trimmed.matches("^\\d{5,6}$")) {
            String prefix = detectNumericMarket(trimmed);
            if (prefix != null) return prefix + trimmed;
        }

        // 4. 纯字母 → 美股
        if (trimmed.matches("(?i)^[a-z]+$")) {
            return "gb_" + trimmed.toLowerCase();
        }

        // 5. 带 . 的 → 美股
        if (trimmed.matches("(?i)^[a-z]+(\\.[a-z]+)?$")) {
            return "gb_" + trimmed.toLowerCase();
        }

        return null;
    }
}
