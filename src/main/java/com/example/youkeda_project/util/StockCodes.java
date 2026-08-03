package com.example.youkeda_project.util;

import java.util.Map;

/**
 * 常用股票名称→代码映射(代码为新浪格式).
 * <p>StockService 与 KLineService 共用,避免两套重复的映射表.
 */
public final class StockCodes {

    private StockCodes() {}

    /** 常用股票名称→代码映射(代码为新浪格式) */
    public static final Map<String, String> NAME_MAP;

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
        NAME_MAP = Map.copyOf(map);
    }

    /** 按名称查找新浪格式代码(不区分大小写),未找到返回 null. */
    public static String byName(String name) {
        if (name == null) return null;
        String code = NAME_MAP.get(name);
        if (code != null) return code;
        return NAME_MAP.get(name.toLowerCase());
    }
}
