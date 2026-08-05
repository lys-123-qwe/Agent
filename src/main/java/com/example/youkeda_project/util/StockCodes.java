package com.example.youkeda_project.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 常用股票名称→代码映射(代码为新浪格式).
 * <p>StockService 与 KLineService 共用,避免两套重复的映射表.
 */
public final class StockCodes {

    private StockCodes() {}

    /** 常用股票名称→代码映射(代码为新浪格式) */
    public static final Map<String, String> NAME_MAP;

    /** 动态反查结果缓存(名称→代码),避免重复请求东财搜索 */
    private static final Map<String, String> SEARCH_CACHE = new ConcurrentHashMap<>();

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
        map.put("中国银行", "sh601988");
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
        map.put("阳光电源", "sz300274");
        map.put("海尔智家", "sh600690");
        map.put("中国电信", "sh601728");
        map.put("中国联通", "sh600050");
        map.put("建设银行", "sh601939");
        map.put("交通银行", "sh601328");
        map.put("邮储银行", "sh601658");
        map.put("兴业银行", "sh601166");
        map.put("浦发银行", "sh600000");
        map.put("民生银行", "sh600016");
        map.put("中信证券", "sh600030");
        map.put("东方财富", "sz300059");
        map.put("中金公司", "sh601995");
        map.put("华泰证券", "sh601688");
        map.put("国泰君安", "sh601211");
        map.put("恒瑞医药", "sh600276");
        map.put("迈瑞医疗", "sz300760");
        map.put("片仔癀", "sh600436");
        map.put("云南白药", "sz000538");
        map.put("爱尔眼科", "sz300015");
        map.put("立讯精密", "sz002475");
        map.put("京东方A", "sz000725");
        map.put("紫金矿业", "sh601899");
        map.put("中国神华", "sh601088");
        map.put("陕西煤业", "sh601225");
        map.put("三一重工", "sh600031");
        map.put("潍柴动力", "sz000338");
        map.put("海天味业", "sh603288");
        map.put("伊利股份", "sh600887");
        map.put("农夫山泉港股", "hk09633");
        map.put("小米集团", "hk01810");
        map.put("中芯国际港股", "hk00981");
        map.put("阿里巴巴美股", "gb_baba");
        map.put("京东美股", "gb_jd");
        map.put("百度美股", "gb_bidu");
        map.put("蔚来", "gb_nio");
        map.put("理想汽车美股", "gb_li");
        map.put("小鹏汽车美股", "gb_xpev");
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
        // === 日股 ===
        map.put("丰田", "jp_7203");
        map.put("丰田汽车", "jp_7203");
        map.put("索尼", "jp_6758");
        map.put("索尼集团", "jp_6758");
        map.put("任天堂", "jp_7974");
        map.put("软银", "jp_9984");
        map.put("软银集团", "jp_9984");
        map.put("日立", "jp_6501");
        map.put("本田", "jp_7267");
        map.put("本田汽车", "jp_7267");
        map.put("三菱UFJ", "jp_8306");
        // === 韩股 ===
        map.put("三星电子", "kr_005930");
        map.put("三星", "kr_005930");
        map.put("SK海力士", "kr_000660");
        map.put("海力士", "kr_000660");
        map.put("现代汽车", "kr_005380");
        map.put("现代", "kr_005380");
        map.put("LG化学", "kr_051910");
        map.put("起亚", "kr_000270");
        map.put("起亚汽车", "kr_000270");
        map.put("NAVER", "kr_035420");
        map.put("三星生物", "kr_207940");
        NAME_MAP = Map.copyOf(map);
    }

    /** 按名称查找新浪格式代码(不区分大小写).映射未命中时动态反查,仍失败返回 null. */
    public static String byName(String name) {
        if (name == null) return null;
        String code = NAME_MAP.get(name);
        if (code != null) return code;
        code = NAME_MAP.get(name.toLowerCase());
        if (code != null) return code;
        // 名称映射未命中:动态调用东财搜索反查
        return searchCode(name);
    }

    /**
     * 按名称动态反查代码(名称映射未命中时调用东财搜索接口).
     * 返回项目统一的新浪格式代码(sh/sz/hk/gb_),未找到返回 null.
     */
    public static String searchCode(String name) {
        if (name == null || name.isBlank()) return null;
        String cached = SEARCH_CACHE.get(name.trim());
        if (cached != null) return cached;
        try {
            String url = "https://searchapi.eastmoney.com/api/suggest/get?input="
                + java.net.URLEncoder.encode(name.trim(), java.nio.charset.StandardCharsets.UTF_8)
                + "&type=14&token=D43BF722C8E33BDC906FB84D85E326E8&count=8";
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5)).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Referer", "https://quote.eastmoney.com")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(java.time.Duration.ofSeconds(8))
                .GET().build();
            java.net.http.HttpResponse<String> resp = client.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            String body = resp.body();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"Code\":\"([^\"]+)\".*?\"Classify\":\"([^\"]+)\".*?\"QuoteID\":\"([^\"]+)\"").matcher(body);
            // 优先 A股,其次港股,最后美股
            String fallback = null;
            while (m.find()) {
                String code = m.group(1);
                String classify = m.group(2);
                String quoteId = m.group(3);
                if ("AStock".equals(classify)) {
                    String prefix = quoteId.startsWith("1.") ? "sh" : "sz";
                    String result = prefix + code;
                    SEARCH_CACHE.put(name.trim(), result);
                    return result;
                }
                if (fallback == null) {
                    if ("HK".equals(classify)) fallback = "hk" + code;
                    else if ("UsStock".equals(classify)) fallback = "gb_" + code.toLowerCase();
                }
            }
            if (fallback != null) SEARCH_CACHE.put(name.trim(), fallback);
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    /** 根据新浪格式代码返回市场标签（用于图表副标题等） */
    public static String marketLabel(String code) {
        if (code == null) return "A股";
        if (code.startsWith("sh")) return "上证";
        if (code.startsWith("sz")) return "深证";
        if (code.startsWith("bj")) return "北交所";
        if (code.startsWith("hk")) return "港股";
        if (code.startsWith("gb_")) return "美股";
        return "A股";
    }

    /** 宽松解析数值字符串为 long（支持纯数字与小数），失败返回 0 */
    public static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }

    /**
     * 将股票代码/名称解析为东方财富 secid。
     * 支持：已带 secid(1./0.)、sh/sz/bj 前缀新浪格式、纯 6 位数字。
     */
    public static String toEastMoneySecid(String input) {
        if (input == null) return null;
        String t = input.trim();
        if (t.matches("^[01]\\.[0-9]{6}$")) return t;
        if (t.matches("(?i)^sh[0-9]{6}$")) return "1." + t.substring(2);
        if (t.matches("(?i)^sz[0-9]{6}$")) return "0." + t.substring(2);
        if (t.matches("(?i)^bj[0-9]{6}$")) return "0." + t.substring(2);
        if (t.matches("^[0-9]{6}$")) {
            char first = t.charAt(0);
            return switch (first) {
                case '6', '5' -> "1." + t;
                case '0', '3', '1', '8', '4' -> "0." + t;
                default -> null;
            };
        }
        return null;
    }
}
