package com.example.youkeda_project.service.stock;

import com.example.youkeda_project.util.StockCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.youkeda_project.util.FormatUtil;
import com.example.youkeda_project.util.SparklineUtil;
import com.example.youkeda_project.util.StockQuoteParser;
import com.example.youkeda_project.util.TtlCache;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private static final long KLINE_CACHE_TTL_MS = 300_000; // 5分钟K线缓存

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private final RestClient restClient;

    // K线数据缓存: code+days -> [timestamp, result]
    private final TtlCache<List<KLineService.KLineData>> klineCache = new TtlCache<>(KLINE_CACHE_TTL_MS);

    private final KLineService kLineService;

    public StockService(KLineService kLineService) {
        this.restClient = RestClient.create();
        this.kLineService = kLineService;
    }

    /** 日股/韩股 Mock 数据: code → 5个工作日(周一~周五)的 [开盘,最高,最低,收盘,成交量] */
    private static final Map<String, List<String[]>> MOCK_STOCK_DATA;

    static {
        Map<String, List<String[]>> m = new java.util.HashMap<>();

        // ===== 日股 (价格单位: 日元 JPY) =====
        // 丰田汽车 7203
        m.put("jp_7203", List.of(
            new String[]{"3120","3150","3100","3140","35000000"},
            new String[]{"3145","3180","3130","3160","32000000"},
            new String[]{"3165","3170","3120","3130","38000000"},
            new String[]{"3125","3150","3110","3145","30000000"},
            new String[]{"3150","3185","3140","3170","36000000"}
        ));
        // 索尼 6758
        m.put("jp_6758", List.of(
            new String[]{"3480","3520","3460","3500","18000000"},
            new String[]{"3505","3540","3490","3530","17000000"},
            new String[]{"3535","3550","3500","3510","19000000"},
            new String[]{"3505","3530","3490","3520","16000000"},
            new String[]{"3525","3560","3510","3550","17500000"}
        ));
        // 任天堂 7974
        m.put("jp_7974", List.of(
            new String[]{"8450","8580","8420","8550","8000000"},
            new String[]{"8560","8650","8530","8620","7500000"},
            new String[]{"8630","8680","8550","8570","8200000"},
            new String[]{"8560","8620","8520","8600","7800000"},
            new String[]{"8610","8700","8590","8680","7200000"}
        ));
        // 软银集团 9984
        m.put("jp_9984", List.of(
            new String[]{"208","212","206","210","50000000"},
            new String[]{"211","214","209","213","48000000"},
            new String[]{"213","215","210","211","52000000"},
            new String[]{"210","213","208","212","45000000"},
            new String[]{"213","217","212","215","55000000"}
        ));
        // 三菱UFJ 8306
        m.put("jp_8306", List.of(
            new String[]{"1830","1860","1820","1850","40000000"},
            new String[]{"1855","1880","1840","1870","38000000"},
            new String[]{"1875","1890","1850","1860","42000000"},
            new String[]{"1855","1870","1840","1865","36000000"},
            new String[]{"1870","1900","1860","1890","39000000"}
        ));
        // 本田 7267
        m.put("jp_7267", List.of(
            new String[]{"1820","1850","1810","1840","25000000"},
            new String[]{"1845","1870","1830","1860","24000000"},
            new String[]{"1865","1880","1840","1850","26000000"},
            new String[]{"1845","1870","1830","1865","23000000"},
            new String[]{"1870","1900","1860","1890","25000000"}
        ));
        // 东京电子 8035
        m.put("jp_8035", List.of(
            new String[]{"25800","26300","25600","26100","5000000"},
            new String[]{"26150","26600","26000","26500","4800000"},
            new String[]{"26550","26800","26200","26300","5200000"},
            new String[]{"26250","26500","26000","26400","4500000"},
            new String[]{"26450","27000","26300","26800","5500000"}
        ));
        // 迅销(优衣库) 9983
        m.put("jp_9983", List.of(
            new String[]{"45500","46200","45300","46000","3000000"},
            new String[]{"46100","46800","45900","46600","2800000"},
            new String[]{"46700","47000","46200","46400","3200000"},
            new String[]{"46300","46800","46000","46700","2900000"},
            new String[]{"46800","47500","46600","47300","3100000"}
        ));

        // ===== 韩股 (价格单位: 韩元 KRW) =====
        // 三星电子 005930
        m.put("kr_005930", List.of(
            new String[]{"74500","75500","74200","75200","15000000"},
            new String[]{"75300","76200","75000","76000","14000000"},
            new String[]{"76100","76500","75500","75700","16000000"},
            new String[]{"75600","76300","75300","76100","14500000"},
            new String[]{"76200","77200","76000","77000","15500000"}
        ));
        // SK海力士 000660
        m.put("kr_000660", List.of(
            new String[]{"182000","186000","181000","185000","5000000"},
            new String[]{"185500","189000","184000","188000","4800000"},
            new String[]{"188500","190000","185000","186000","5200000"},
            new String[]{"185500","188000","184000","187000","4600000"},
            new String[]{"187500","192000","186000","191000","5100000"}
        ));
        // LG新能源 373220
        m.put("kr_373220", List.of(
            new String[]{"355000","362000","353000","360000","2000000"},
            new String[]{"361000","368000","359000","366000","1900000"},
            new String[]{"367000","370000","362000","364000","2100000"},
            new String[]{"363000","368000","360000","367000","1850000"},
            new String[]{"368000","375000","366000","373000","2050000"}
        ));
        // 现代汽车 005380
        m.put("kr_005380", List.of(
            new String[]{"232000","236000","230000","235000","3000000"},
            new String[]{"235500","239000","234000","238000","2900000"},
            new String[]{"238500","240000","235000","236000","3100000"},
            new String[]{"235500","238000","233000","237000","2800000"},
            new String[]{"237500","242000","236000","241000","3050000"}
        ));
        // 起亚 000270
        m.put("kr_000270", List.of(
            new String[]{"103000","106000","102500","105500","2500000"},
            new String[]{"105800","108000","105000","107500","2400000"},
            new String[]{"107800","109000","106000","106500","2600000"},
            new String[]{"106200","108000","105500","107800","2350000"},
            new String[]{"108000","110500","107500","110000","2550000"}
        ));
        // 三星生物 207940
        m.put("kr_207940", List.of(
            new String[]{"810000","825000","808000","822000","800000"},
            new String[]{"823000","835000","820000","832000","750000"},
            new String[]{"833000","838000","825000","828000","820000"},
            new String[]{"827000","835000","823000","833000","780000"},
            new String[]{"834000","845000","832000","842000","790000"}
        ));

        MOCK_STOCK_DATA = Map.copyOf(m);
    }

    /** 获取当前日期对应的 Mock 数据日索引(0=周一, 4=周五, 周末返回周五) */
    private static int getMockDayIndex() {
        int dow = java.time.LocalDate.now().getDayOfWeek().getValue(); // 1=Mon..7=Sun
        return switch (dow) {
            case 1 -> 0;  // Monday
            case 2 -> 1;  // Tuesday
            case 3 -> 2;  // Wednesday
            case 4 -> 3;  // Thursday
            default -> 4; // Friday/Saturday/Sunday → Friday
        };
    }

    /** 返回指定日索引对应的日期标签(如"周一")，用于提示数据时效性 */
    private static String getMockDayLabel(int dayIndex) {
        return switch (dayIndex) {
            case 0 -> "周一";
            case 1 -> "周二";
            case 2 -> "周三";
            case 3 -> "周四";
            case 4 -> "周五";
            default -> "";
        };
    }

    /**
     * 查询股票实时行情。支持股票名称、纯数字代码、字母代码、带前缀代码。
     *
     * @param input 股票名称或代码，如 "贵州茅台"、"600519"、"sh600519"、"腾讯"、"AAPL"、"gb_aapl"
     * @return 格式化的行情字符串
     */
    /** 查询股票/指数实时行情（支持名称/代码），失败返回错误文本。 */
    public String queryStock(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "错误: 请输入股票名称或代码\n"
                + "示例: 贵州茅台 / 600519 / sh600519 / 腾讯 / AAPL";
        }
        String trimmed = input.trim();

        // int_ 前缀 → 全球指数（走独立接口）
        if (trimmed.matches("(?i)^int_[a-z0-9.]+$")) {
            return fetchIntlIndex(trimmed.toLowerCase());
        }

        String fullCode = resolveStockCode(trimmed);
        if (fullCode == null) {
            return "错误: 不支持的股票 '" + trimmed + "'\n"
                + "支持的格式: 股票名称（贵州茅台/腾讯/苹果/丰田/三星电子）、代码（600519/00700/AAPL）、"
                + "带前缀（sh600519/hk00700/gb_aapl/jp_7203/kr_005930）";
        }
        return fetchStockData(fullCode, trimmed);
    }

    /**
     * 根据纯数字代码判断所属交易所。
     * A 股 6 位: 6xxxxx→上证, 0xxxxx/3xxxxx→深证, 4xxxxx/8xxxxx→北交所
     * 港股 5 位: 0xxxx→港股（如 00700）
     */
    /** 根据纯数字代码首字符判断市场（6/5=沪，其余=深）。 */

    /**
     * 解析内部代码，提取市场类型。
     */
    /** 根据新浪代码前缀返回市场类型（sh/sz/hk/gb_）。 */
    private String getMarketType(String fullCode) {
        if (fullCode.startsWith("gb_")) return "us";
        if (fullCode.startsWith("int_")) return "intl";
        if (fullCode.startsWith("jp_")) return "jp";
        if (fullCode.startsWith("kr_")) return "kr";
        if (fullCode.startsWith("hk")) return "hk";
        return "a";
    }

    // ==================== 拉取数据 ====================

    /**
     * 先尝试新浪，失败回退腾讯。成功时附加近期走势火花图。
     */
    /** 拉取行情数据：新浪优先，失败回退腾讯。 */
    private String fetchStockData(String fullCode, String displayName) {
        log.info("查询股票: code={}, display={}", fullCode, displayName);

        // 日股/韩股使用 Mock 数据（实时接口不可用）
        if (fullCode.startsWith("jp_") || fullCode.startsWith("kr_")) {
            return fetchMockStockData(fullCode, displayName);
        }

        String result;
        try {
            result = fetchFromSina(fullCode);
        } catch (Exception e) {
            log.warn("新浪接口查询失败: {}, 回退到腾讯接口", e.getMessage());
            try {
                result = fetchFromTencent(fullCode);
            } catch (Exception e2) {
                log.error("腾讯接口也查询失败: {}", e2.getMessage());
                return "错误: 股票查询失败，两个接口均不可用 - " + e2.getMessage();
            }
        }
        return appendSparkline(fullCode, result);
    }

    /**
     * 尝试拉取近期收盘价并生成火花图，附加到查询结果前。
     * 失败不影响主流程。
     */
    /** 在行情文本前附加近期走势火花图，失败不影响主流程。 */
    private String appendSparkline(String fullCode, String existingResult) {
        try {
            List<BigDecimal> closes = fetchRecentCloses(fullCode);
            if (closes.size() >= 3) {
                String sparkline = SparklineUtil.generate(closes);
                String label = "近" + closes.size() + "日走势";
                // 提取当前涨跌方向
                String direction = existingResult.contains("📈") ? "📈"
                        : existingResult.contains("📉") ? "📉" : "";
                StringBuilder sb = new StringBuilder();
                sb.append("  ").append(sparkline).append(" ").append(label).append(" ").append(direction).append("\n\n");
                sb.append(existingResult);
                return sb.toString();
            }
        } catch (Exception e) {
            log.debug("火花图生成失败（不影响主流程）: {}", e.getMessage());
        }
        return existingResult;
    }

    /**
     * 日股/韩股 Mock 数据查询（实时接口不可用，使用内置模拟数据）。
     * 根据当前星期几自动选择对应工作日的数据，非交易日提示数据日期。
     */
    private String fetchMockStockData(String fullCode, String displayName) {
        List<String[]> weekData = MOCK_STOCK_DATA.get(fullCode);
        if (weekData == null || weekData.isEmpty()) {
            return "错误: 暂无 " + displayName + "（" + fullCode + "）的行情数据";
        }

        int dayIndex = getMockDayIndex();
        String[] today = weekData.get(dayIndex);

        String open = today[0];
        String high = today[1];
        String low = today[2];
        String close = today[3];
        String volume = today[4];

        // 昨收 = 前一个工作日的收盘价；周一则用上周五模拟
        String prevClose;
        if (dayIndex > 0) {
            prevClose = weekData.get(dayIndex - 1)[3];
        } else {
            // 周一：用上周五收盘价（基于当天收盘价倒推小幅波动）
            try {
                BigDecimal closeBd = new BigDecimal(close);
                BigDecimal prevBd = closeBd.multiply(new BigDecimal("0.998"))
                    .setScale(0, RoundingMode.HALF_UP);
                prevClose = prevBd.toPlainString();
            } catch (NumberFormatException e) {
                prevClose = close;
            }
        }

        String todayStr = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String formatted = formatStockInfo(displayName, fullCode, close, open, prevClose, high, low, volume, todayStr, "");

        // 周末/非交易日提示
        String marketLabel = fullCode.startsWith("jp_") ? "日股" : "韩股";
        int dow = java.time.LocalDate.now().getDayOfWeek().getValue();
        String prefix = "";
        if (dow >= 6) {
            // 周末：标注这是周五的数据
            prefix = "⚠️ 当前为非交易日，以下为最近交易日（周五）收盘数据，仅供参考\n\n";
        }

        // 生成 sparkline（用5日收盘价）
        try {
            List<BigDecimal> closes = new ArrayList<>();
            for (String[] day : weekData) {
                closes.add(new BigDecimal(day[3]));
            }
            if (closes.size() >= 3) {
                String sparkline = SparklineUtil.generate(closes);
                String direction = formatted.contains("📈") ? "📈"
                        : formatted.contains("📉") ? "📉" : "";
                String label = "近5日走势";
                return prefix + "  " + sparkline + " " + label + " " + direction + "\n\n" + formatted;
            }
        } catch (Exception e) {
            log.debug("Mock火花图生成失败: {}", e.getMessage());
        }

        return prefix + formatted;
    }

    /**
     * 从腾讯日K线接口拉取最近 8 个交易日的收盘价（前复权）。
     */
    /** 从腾讯接口拉取近期收盘价序列（供火花图使用）。 */
    private List<BigDecimal> fetchRecentCloses(String fullCode) {
        // 日股/韩股使用 Mock 收盘价
        if (fullCode.startsWith("jp_") || fullCode.startsWith("kr_")) {
            List<String[]> weekData = MOCK_STOCK_DATA.get(fullCode);
            if (weekData != null) {
                List<BigDecimal> closes = new ArrayList<>();
                for (String[] day : weekData) {
                    closes.add(new BigDecimal(day[3]));
                }
                return closes;
            }
            return List.of();
        }

        String tencentCode = toTencentCode(fullCode);

        try {
            String url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param="
                    + tencentCode + ",day,,,30,qfq";
            String response = restClient.get()
                    .uri(url)
                    .header("Referer", "https://finance.qq.com")
                    .retrieve()
                    .body(String.class);

            @SuppressWarnings("unchecked")
            var map = OBJECT_MAPPER.readValue(response, Map.class);
            if (map == null || map.get("data") == null) return List.of();

            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) map.get("data");
            @SuppressWarnings("unchecked")
            var stockData = (Map<String, Object>) data.get(tencentCode);
            if (stockData == null) {
                stockData = (Map<String, Object>) data.get(tencentCode.toLowerCase());
            }
            if (stockData == null) return List.of();

            // 优先取前复权，无则取不复权
            @SuppressWarnings("unchecked")
            var klines = (List<List<String>>) stockData.get("qfqday");
            if (klines == null || klines.isEmpty()) {
                klines = (List<List<String>>) stockData.get("day");
            }
            if (klines == null || klines.isEmpty()) return List.of();

            List<BigDecimal> closes = new ArrayList<>();
            for (List<String> item : klines) {
                // 腾讯K线格式: [日期, 开盘, 收盘, 最高, 最低, 成交量]
                if (item.size() >= 3) {
                    closes.add(new BigDecimal(item.get(2))); // 收盘价在索引2
                }
            }
            // 腾讯接口可能多返回1条(含当前不完整日),截取到30条,保证火花图标签固定为"近30日走势"
            if (closes.size() > 30) {
                return closes.subList(0, 30);
            }
            return closes;
        } catch (Exception e) {
            log.debug("获取近K线失败(code={}): {}", fullCode, e.getMessage());
            return List.of();
        }
    }

    // ==================== 新浪接口 ====================

    /**
     * 新浪股票接口。根据市场类型分别解析。
     */
    /** 调用新浪行情接口并返回原始响应。 */
    private String fetchFromSina(String fullCode) {
        String response = restClient.get()
                .uri(SINA_API + fullCode)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RuntimeException("新浪返回空数据");
        }

        log.info("新浪响应(code={}): {} chars", fullCode, response.length());

        String[] fields = StockQuoteParser.extractQuotedFields(response, ",");
        if (fields == null) {
            if (response.indexOf('"') == -1) {
                throw new RuntimeException("新浪响应格式异常: 无引号包裹");
            }
            throw new RuntimeException("新浪返回空行情数据，股票代码可能无效或无数据");
        }
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
    /** 解析新浪 A 股行情字段并格式化为文本。 */
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
        // 新浪A股末尾字段: 日期(30), 时间(31)
        String date = fields.length > 30 ? fields[30] : "";
        String time = fields.length > 31 ? fields[31] : "";
        return formatStockInfo(name, code, price, open, prevClose, high, low, volume, date, time);
    }

    /**
     * 解析新浪港股响应。
     * 字段: 英文名,今开,昨收,当前价,最高,最低,买价,卖价,成交量,成交额,日期,时间,...
     */
    /** 解析新浪港股行情字段并格式化为文本。 */
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
        // 港股末尾字段: 日期(17), 时间(18), 格式 2026/08/03
        String date = fields.length > 17 ? fields[17].replace("/", "-") : "";
        String time = fields.length > 18 ? fields[18] : "";
        return formatStockInfo(engName, code, price, open, prevClose, high, low, volume, date, time);
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
    /** 解析新浪美股行情字段并格式化为文本。 */
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

        // 美股末尾字段: 日期, 时间
        String date = fields.length > 25 ? fields[len - 2] : "";
        String time = fields.length > 26 ? fields[len - 1] : "";
        // 美股用 $ 符号
        return formatUSStockInfo(name, code, price, prevClose, open, high, low, volume, changePctStr, date, time);
    }

    // ==================== 腾讯接口（回退） ====================

    /**
     * 腾讯股票接口。需要对美股代码做转换: gb_aapl → usAAPL
     */
    /** 调用腾讯行情接口查询（新浪失败时的回退数据源）。 */
    private String fetchFromTencent(String fullCode) {
        String tencentCode = toTencentCode(fullCode);

        String response = restClient.get()
                .uri(TENCENT_API + tencentCode)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RuntimeException("腾讯返回空数据");
        }

        log.info("腾讯响应(code={}): {} chars", fullCode, response.length());

        String[] fields = StockQuoteParser.extractQuotedFields(response, "~");
        if (fields == null) {
            if (response.indexOf('"') == -1) {
                throw new RuntimeException("腾讯响应格式异常");
            }
            throw new RuntimeException("腾讯返回空行情数据");
        }

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
        // 腾讯字段: f[30] 是完整时间戳(如 20260803141010)
        String date = "";
        String time = "";
        if (fields.length > 30 && fields[30] != null && fields[30].length() >= 14) {
            String ts = fields[30];
            date = ts.substring(0, 4) + "-" + ts.substring(4, 6) + "-" + ts.substring(6, 8);
            time = ts.substring(8, 10) + ":" + ts.substring(10, 12) + ":" + ts.substring(12, 14);
        }

        String market = getMarketType(fullCode);
        if ("us".equals(market)) {
            return formatUSStockInfo(name, fullCode, price, prevClose, open, high, low, volume, null, date, time);
        }
        return formatStockInfo(name, fullCode, price, open, prevClose, high, low, volume, date, time);
    }

    /**
     * 将新浪格式代码转为腾讯格式。
     * gb_aapl → usAAPL, hk00700 → hk00700, sh600519 → sh600519
     */
    /** 把新浪格式代码转成腾讯格式代码。 */
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
                                    String low, String volume, String dataDate, String dataTime) {
        try {
            BigDecimal price = new BigDecimal(currentPrice);
            BigDecimal prevClose = new BigDecimal(yesterdayClose);

            FormatUtil.ChangeInfo ci = FormatUtil.calcChange(price, prevClose, 2);

            String marketLabel = getMarketLabel(code);

            StringBuilder sb = new StringBuilder();
            sb.append(name).append("（").append(code).append("）").append(ci.direction()).append("\n");
            sb.append("  市场: ").append(marketLabel).append("\n");
            sb.append("  当前价: ").append(price).append("\n");
            sb.append("  涨跌额: ").append(ci.sign()).append(ci.change()).append("\n");
            sb.append("  涨跌幅: ").append(ci.sign()).append(ci.changePct()).append("%\n");
            sb.append("  今开: ").append(open).append("\n");
            sb.append("  昨收: ").append(yesterdayClose).append("\n");
            if (!high.isBlank() && !"—".equals(high)) {
                sb.append("  最高: ").append(high).append("\n");
            }
            if (!low.isBlank() && !"—".equals(low)) {
                sb.append("  最低: ").append(low).append("\n");
            }
            sb.append("  成交量: ").append(formatVolume(volume)).append("\n");
            sb.append("  🕐 数据时间: ").append(formatQuoteTime(dataDate, dataTime));
            return sb.toString();
        } catch (NumberFormatException e) {
            log.warn("价格格式化失败: price={}, prevClose={}", currentPrice, yesterdayClose);
            return name + "（" + code + "）\n  当前价: " + currentPrice
                    + "\n  今开: " + open + "\n  昨收: " + yesterdayClose;
        }
    }

    /** 格式化行情数据时间;若非今天开盘时段数据,标注为历史数据. */
    private String formatQuoteTime(String date, String time) {
        if (date == null || date.isBlank()) return "未知";
        String ts = date + " " + (time != null ? time : "");
        if (FormatUtil.isToday(date)) {
            return ts;
        }
        return ts + "（⚠️非今日数据，可能未开盘或已收盘）";
    }

    /**
     * 格式化美股行情（价格以美元计）。
     */
    private String formatUSStockInfo(String name, String code, String currentPrice,
                                      String yesterdayClose, String open, String high,
                                      String low, String volume, String changePctStr,
                                      String dataDate, String dataTime) {
        try {
            BigDecimal price = new BigDecimal(currentPrice);

            String direction = "📈";
            String changeStr = "—";
            String sign = "";

            if (!"—".equals(yesterdayClose) && !yesterdayClose.isBlank()) {
                BigDecimal prevClose = new BigDecimal(yesterdayClose);
                BigDecimal change = price.subtract(prevClose);
                direction = FormatUtil.direction(change);
                sign = FormatUtil.sign(change);
                changeStr = sign + change.toPlainString();
            } else if (changePctStr != null && !changePctStr.isBlank() && !"0.00".equals(changePctStr)) {
                // 从涨跌幅推断涨跌方向
                try {
                    double pct = Double.parseDouble(changePctStr);
                    direction = FormatUtil.direction(BigDecimal.valueOf(pct));
                    sign = FormatUtil.sign(BigDecimal.valueOf(pct));
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
            sb.append("  成交量: ").append(formatVolume(volume)).append("\n");
            sb.append("  🕐 数据时间: ").append(formatQuoteTime(dataDate, dataTime));
            return sb.toString();
        } catch (NumberFormatException e) {
            log.warn("美股价格格式化失败: price={}", currentPrice);
            return name + "（" + code + "）\n  当前价: $" + currentPrice;
        }
    }

    /**
     * 格式化成交量。
     */
    /** 成交量格式化（自动转换为万/亿）。 */
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
    /** 返回市场中文标签（上证/深证/港股/美股）。 */
    private String getMarketLabel(String code) {
        if (code.startsWith("sh")) return "上证 A 股";
        if (code.startsWith("sz")) return "深证 A 股";
        if (code.startsWith("bj")) return "北交所";
        if (code.startsWith("hk")) return "港股";
        if (code.startsWith("gb_")) return "美股";
        if (code.startsWith("int_")) return "全球指数";
        if (code.startsWith("jp_")) return "日股";
        if (code.startsWith("kr_")) return "韩股";
        return "A 股";
    }

    // ==================== 国际指数 ====================

    /** 通过新浪国际指数接口获取行情（日经/道琼斯/纳斯达克/标普500） */
    private String fetchIntlIndex(String code) {
        try {
            String response = restClient.get()
                .uri(SINA_API + code)
                .header("Referer", "https://finance.sina.com.cn")
                .retrieve()
                .body(String.class);

            if (response == null || response.isBlank()) return "错误: 未获取到指数数据";

            String[] fields = StockQuoteParser.extractQuotedFields(response, ",");
            if (fields == null) {
                if (response.indexOf('"') == -1) return "错误: 指数数据格式异常";
                return "错误: 指数数据为空，代码 " + code;
            }

            // 新浪 int_ 格式: 名称,当前价,涨跌额,涨跌幅
            if (fields.length < 4) return "错误: 指数字段不足";

            String name = fields[0].trim();
            String price = fields[1];
            String change = fields[2];
            String changePct = fields[3];

            String direction = change.startsWith("-") ? "📉" : "📈";
            String sign = change.startsWith("-") ? "" : "+";

            return String.format("%s（%s）%s\n  市场: 全球指数\n  当前: %s\n  涨跌: %s%s (%s%s%%)",
                name, code, direction, price, sign, change, sign, changePct);
        } catch (Exception e) {
            log.error("国际指数查询失败({}): {}", code, e.getMessage());
            return "错误: 国际指数查询失败 - " + e.getMessage();
        }
    }

    // ==================== 历史K线 ====================

    /** K线数据记录 */

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
    /** 查询基本面（PE/PB/市值），东财接口 + 失败重试，失败返回 null。 */
    public FundamentalData getFundamentals(String input) {
        String code = resolveStockCode(input);
        if (code == null) return null;

        // 东方财富 secid 格式: 1.600519(上证), 0.000001(深证)
        String secid = com.example.youkeda_project.util.StockCodes.toEastMoneySecid(code);
        if (secid == null) return null;

        // 东财接口偶发断开,失败重试 5 次,间隔递增
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return fetchFundamentals(secid);
            } catch (Exception e) {
                log.debug("基本面查询失败(第{}次): {}", attempt + 1, e.getMessage());
                try { Thread.sleep(400L * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        return null;
    }

    /** 调用东财接口拉取并解析基本面数据。 */
    private FundamentalData fetchFundamentals(String secid) throws Exception {
        try {
            String urlStr = "https://push2.eastmoney.com/api/qt/stock/get?secid=" + secid
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

            com.fasterxml.jackson.databind.JsonNode root = OBJECT_MAPPER.readTree(response);
            com.fasterxml.jackson.databind.JsonNode data = root.path("data");

            if (data.isMissingNode()) return null;

            String pe = formatFundamentalValue(data.path("f162").asText(""), 2);
            String pb = formatFundamentalValue(data.path("f167").asText(""), 2);
            String marketCap = formatMarketCap(data.path("f116").asText(""));
            String circulatingCap = formatMarketCap(data.path("f117").asText(""));

            return new FundamentalData(pe, "", pb, marketCap, circulatingCap, "—", "—", "", "");
        } catch (Exception e) {
            log.warn("基本面数据查询失败: code={}, error={}", secid, e.getMessage());
            throw e;
        }
    }

    /**
     * 将新浪格式代码转为东方财富 secid。
     */
    /** 格式化基本面数值（null/空/"null"/"-" 显示为"—"）。 */
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

    /** 格式化市值（自动转换为亿/万亿）。 */
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
    /** 解析字符串为 double，失败返回 0。 */
    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (Exception e) { return 0; }
    }

    /** 获取历史 K 线数据（带 5 分钟缓存，避免重复请求）。 */
    public List<KLineService.KLineData> getHistoricalKline(String input, int days) {
        String code = resolveStockCode(input);
        if (code == null) return List.of();

        // 检查缓存
        String cacheKey = code + ":" + days;
        List<KLineService.KLineData> cached = klineCache.get(cacheKey);
        if (cached != null) {
            log.debug("K线缓存命中: key={}", cacheKey);
            return cached;
        }

        // 获取数据
        List<KLineService.KLineData> result;
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            result = getAStockKline(code, days);
        } else if (code.startsWith("hk")) {
            result = getHKStockKline(code, days);
        } else if (code.startsWith("gb_")) {
            result = getUSStockKline(code, days);
        } else if (code.startsWith("jp_") || code.startsWith("kr_")) {
            result = getMockKline(code, days);
        } else {
            result = List.of();
        }

        // 写入缓存
        if (!result.isEmpty()) {
            klineCache.put(cacheKey, result);
        }

        return result;
    }

    /** A股K线（新浪API） */
    private List<KLineService.KLineData> getAStockKline(String code, int days) {
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

            com.fasterxml.jackson.databind.JsonNode arr = OBJECT_MAPPER.readTree(json);

            List<KLineService.KLineData> result = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : arr) {
                String date = item.path("day").asText("");
                String open = item.path("open").asText("");
                String high = item.path("high").asText("");
                String low = item.path("low").asText("");
                String close = item.path("close").asText("");
                String volume = item.path("volume").asText("0");
                if (!date.isEmpty() && !close.isEmpty()) {
                    result.add(new KLineService.KLineData(date, parseDouble(open), parseDouble(high), parseDouble(low), parseDouble(close), parseDouble(volume)));
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
    private List<KLineService.KLineData> getHKStockKline(String code, int days) {
        String url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param="
            + code + ",day,,," + (days + 20) + ",qfq";

        try {
            String response = restClient.get()
                .uri(url)
                .header("Referer", "https://web.ifzq.gtimg.cn")
                .retrieve()
                .body(String.class);

            if (response == null || response.isBlank()) return List.of();

            com.fasterxml.jackson.databind.JsonNode root = OBJECT_MAPPER.readTree(response);

            // data.hk00700.day = [[date, open, close, high, low, volume], ...]
            com.fasterxml.jackson.databind.JsonNode dataNode = root.path("data").path(code).path("day");
            if (dataNode.isMissingNode() || !dataNode.isArray()) {
                // 尝试 qfqday 字段
                dataNode = root.path("data").path(code).path("qfqday");
            }
            if (dataNode.isMissingNode() || !dataNode.isArray()) return List.of();

            List<KLineService.KLineData> result = new ArrayList<>();
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
                    result.add(new KLineService.KLineData(date, parseDouble(open), parseDouble(high), parseDouble(low), parseDouble(close), parseDouble(volume)));
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
    private List<KLineService.KLineData> getUSStockKline(String code, int days) {
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

            com.fasterxml.jackson.databind.JsonNode root = OBJECT_MAPPER.readTree(json);

            List<KLineService.KLineData> result = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : root) {
                String date = item.path("d").asText("");
                String open = item.path("o").asText("");
                String high = item.path("h").asText("");
                String low = item.path("l").asText("");
                String close = item.path("c").asText("");
                String volume = item.path("v").asText("0");
                if (!date.isEmpty() && !close.isEmpty()) {
                    result.add(new KLineService.KLineData(date, parseDouble(open), parseDouble(high), parseDouble(low), parseDouble(close), parseDouble(volume)));
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

    /** 日股/韩股 Mock K线（内置5个工作日数据）。日期使用当前周的周一~周五。 */
    private List<KLineService.KLineData> getMockKline(String code, int days) {
        List<String[]> weekData = MOCK_STOCK_DATA.get(code);
        if (weekData == null || weekData.isEmpty()) return List.of();

        // 生成当前周的周一~周五日期
        java.time.LocalDate today = java.time.LocalDate.now();
        int dow = today.getDayOfWeek().getValue(); // 1=Mon..7=Sun
        java.time.LocalDate monday = today.minusDays(dow - 1);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<KLineService.KLineData> result = new ArrayList<>();
        for (int i = 0; i < weekData.size(); i++) {
            String[] d = weekData.get(i);
            String date = monday.plusDays(i).format(fmt);
            result.add(new KLineService.KLineData(date, parseDouble(d[0]), parseDouble(d[1]), parseDouble(d[2]), parseDouble(d[3]), parseDouble(d[4])));
        }

        if (result.size() > days) {
            result = result.subList(result.size() - days, result.size());
        }

        log.info("Mock K线获取成功: code={}, 数据量={}", code, result.size());
        return result;
    }

    /**
     * 解析股票代码（返回新浪格式完整代码）。
     */
    /** 解析股票代码（返回新浪格式完整代码），统一走 StockCodes.resolveSinaCode。 */
    private String resolveStockCode(String input) {
        return StockCodes.resolveSinaCode(input);
    }
}
