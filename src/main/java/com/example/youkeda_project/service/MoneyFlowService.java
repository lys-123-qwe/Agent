package com.example.youkeda_project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资金流向服务，接入东方财富公开接口。
 * <p>
 * 提供三大类资金流向数据：
 * <ol>
 *   <li>行业板块资金流向排名 — 主力/超大单/大单净流入排行</li>
 *   <li>个股资金流向 — 逐日主力/超大单/大单/中单/小单净流入</li>
 *   <li>北向资金 — 沪股通/深股通每日净买入额</li>
 * </ol>
 */
@Service
public class MoneyFlowService {

    private static final Logger log = LoggerFactory.getLogger(MoneyFlowService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** 东方财富行业板块资金流向字段 */
    private static final String SECTOR_FIELDS = "f2,f3,f12,f14,f62,f66,f69,f72,f75,f78,f81,f84,f87,f184";

    // ==================== 1. 行业板块资金流向排名（最高优先级） ====================

    /**
     * 查询行业板块资金流向排名。
     * <p>
     * 包含每个板块的涨跌幅、主力净流入、超大单/大单/中单/小单净流入。
     * 按主力净流入降序排列。
     *
     * @param topN 取前 N 个板块，默认 15
     */
    public String getSectorMoneyFlow(int topN) {
        if (topN <= 0) topN = 15;
        if (topN > 50) topN = 50;

        try {
            String url = "http://push2.eastmoney.com/api/qt/clist/get"
                    + "?pn=1&pz=" + topN
                    + "&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281"
                    + "&fltt=2&invt=2"
                    + "&fid=f62"   // 按主力净流入排序
                    + "&fs=m:90+t:2"  // 行业板块
                    + "&fields=" + SECTOR_FIELDS;

            log.info("查询行业板块资金流向: topN={}", topN);
            JsonNode root = httpGet(url);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.path("diff").isMissingNode()) {
                return "错误: 东方财富行业板块资金流向数据暂不可用";
            }

            JsonNode diffs = data.path("diff");
            if (!diffs.isArray() || diffs.isEmpty()) {
                return "错误: 行业板块资金流向数据为空";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📊 行业板块资金流向 TOP").append(diffs.size()).append("（主力净流入降序）\n\n");

            // 表头
            sb.append(String.format("  %-10s %-8s %8s %12s %12s %12s%n",
                    "板块", "涨跌幅", "主力净流入", "超大单", "大单", "中单"));
            sb.append("  ").append("─".repeat(70)).append("\n");

            for (JsonNode item : diffs) {
                String name = item.path("f14").asText("-");
                String changePct = fmtNum(item.path("f3").asText()) + "%";
                String mainForce = fmtMoney(item.path("f62").asText());     // 主力净流入
                String superLarge = fmtMoney(item.path("f66").asText());    // 超大单
                String large = fmtMoney(item.path("f72").asText());          // 大单
                String medium = fmtMoney(item.path("f78").asText());         // 中单

                sb.append(String.format("  %-10s %-8s %8s %12s %12s %12s%n",
                        truncate(name, 10), changePct, mainForce, superLarge, large, medium));
            }

            sb.append("\n💡 主力净流入 = 超大单 + 大单，正值表示资金净流入\n");
            sb.append("   数据来源: 东方财富，仅供参考不构成投资建议");

            log.info("行业板块资金流向查询完成: {} 条", diffs.size());
            return sb.toString();

        } catch (Exception e) {
            log.error("行业板块资金流向查询失败", e);
            return "错误: 行业板块资金流向查询失败 - " + e.getMessage();
        }
    }

    /**
     * 查询指定板块名称的资金流向（模糊匹配）。
     */
    public String getSectorByName(String keyword, int topN) {
        if (topN <= 0) topN = 20;

        try {
            String url = "http://push2.eastmoney.com/api/qt/clist/get"
                    + "?pn=1&pz=" + topN
                    + "&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281"
                    + "&fltt=2&invt=2"
                    + "&fid=f62"
                    + "&fs=m:90+t:2"
                    + "&fields=" + SECTOR_FIELDS;

            JsonNode root = httpGet(url);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.path("diff").isMissingNode()) {
                return "错误: 板块资金流向数据暂不可用";
            }

            JsonNode diffs = data.path("diff");
            if (!diffs.isArray()) {
                return "错误: 数据格式异常";
            }

            // 模糊匹配
            List<JsonNode> matched = new ArrayList<>();
            for (JsonNode item : diffs) {
                String name = item.path("f14").asText("");
                if (name.contains(keyword)) {
                    matched.add(item);
                }
            }

            if (matched.isEmpty()) {
                return "未找到包含「" + keyword + "」的行业板块。请尝试更通用的关键词，如[半导体]、[新能源]、[白酒]等。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📊 匹配「").append(keyword).append("」的板块资金流向\n\n");
            sb.append(String.format("  %-10s %-8s %8s %12s %12s %12s%n",
                    "板块", "涨跌幅", "主力净流入", "超大单", "大单", "中单"));
            sb.append("  ").append("─".repeat(70)).append("\n");

            for (JsonNode item : matched) {
                String name = item.path("f14").asText("-");
                String changePct = fmtNum(item.path("f3").asText()) + "%";
                String mainForce = fmtMoney(item.path("f62").asText());
                String superLarge = fmtMoney(item.path("f66").asText());
                String large = fmtMoney(item.path("f72").asText());
                String medium = fmtMoney(item.path("f78").asText());

                sb.append(String.format("  %-10s %-8s %8s %12s %12s %12s%n",
                        truncate(name, 10), changePct, mainForce, superLarge, large, medium));
            }

            sb.append("\n💡 数据来源: 东方财富，仅供参考");
            return sb.toString();

        } catch (Exception e) {
            log.error("板块资金查询失败: keyword={}", keyword, e);
            return "错误: 板块资金查询失败 - " + e.getMessage();
        }
    }

    // ==================== 2. 个股资金流向 ====================

    /**
     * 查询个股每日资金流向。
     * <p>
     * 返回近 N 日的主力/超大单/大单/中单/小单净流入明细。
     *
     * @param stock 股票名称或代码
     * @param days  查询天数，默认 5
     */
    public String getStockMoneyFlow(String stock, int days) {
        if (days <= 0) days = 5;
        if (days > 30) days = 30;

        // 解析股票代码
        String secid = resolveToEastMoneySecid(stock);
        if (secid == null) {
            return "错误: 无法识别股票「" + stock + "」,仅支持A股个股资金流向查询";
        }

        try {
            String url = "http://push2.eastmoney.com/api/qt/stock/fflow/daykline/get"
                    + "?secid=" + secid
                    + "&lmt=" + days
                    + "&fields1=f1,f2,f3"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57";

            log.info("查询个股资金流向: secid={}, days={}", secid, days);
            JsonNode root = httpGet(url);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                return "错误: 个股资金流向数据暂不可用（仅支持A股）";
            }

            JsonNode klines = data.path("klines");
            if (!klines.isArray() || klines.isEmpty()) {
                return "错误: 未获取到「" + stock + "」的资金流向数据";
            }

            String stockName = data.path("name").asText(stock);

            StringBuilder sb = new StringBuilder();
            sb.append("💰 ").append(stockName).append(" 近").append(klines.size()).append("日资金流向\n\n");

            // 表头
            sb.append(String.format("  %-12s %10s %10s %10s %10s %10s%n",
                    "日期", "主力净流入", "超大单", "大单", "中单", "小单"));
            sb.append("  ").append("─".repeat(67)).append("\n");

            for (JsonNode line : klines) {
                String[] parts = line.asText().split(",");
                if (parts.length < 7) continue;
                String date = parts[0];
                String mainForce = fmtMoney(parts[1]);      // f52 主力净流入
                String retail = fmtMoney(parts[2]);          // f53 小单净流入
                String medium = fmtMoney(parts[3]);          // f54 中单净流入
                String large = fmtMoney(parts[4]);           // f55 大单净流入
                String superLarge = fmtMoney(parts[5]);      // f56 超大单净流入

                sb.append(String.format("  %-12s %10s %10s %10s %10s %10s%n",
                        date, mainForce, superLarge, large, medium, retail));
            }

            // 汇总
            sb.append("\n📌 主力净流入 = 超大单 + 大单\n");
            sb.append("   正值 → 主力资金净流入 / 负值 → 主力资金净流出\n");
            sb.append("   数据来源: 东方财富，仅供参考");

            log.info("个股资金流向查询完成: {}", stock);
            return sb.toString();

        } catch (Exception e) {
            log.error("个股资金流向查询失败: stock={}", stock, e);
            return "错误: 个股资金流向查询失败 - " + e.getMessage();
        }
    }

    // ==================== 3. 北向资金 ====================

    /**
     * 查询北向资金（沪股通+深股通）每日净买入。
     * <p>
     * 北向资金 = 外资通过沪深港通买入A股的资金，是重要的市场情绪指标。
     *
     * @param days 查询天数，默认 10
     */
    public String getNorthBoundFlow(int days) {
        if (days <= 0) days = 10;
        if (days > 60) days = 60;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🌏 北向资金（外资流入A股）近况\n\n");

            // 沪股通: secid=1.000001
            String shFlow = fetchNorthBound("1.000001", "沪股通", days);
            // 深股通: secid=0.000001
            String szFlow = fetchNorthBound("0.000001", "深股通", days);

            sb.append(shFlow).append("\n");
            sb.append(szFlow).append("\n");

            // 合并汇总
            sb.append("💡 北向资金 = 外资通过沪港通/深港通买入A股的资金\n");
            sb.append("   净买入为正 → 外资流入看多 / 净买入为负 → 外资流出谨慎\n");
            sb.append("   数据来源: 东方财富，仅供参考");

            return sb.toString();

        } catch (Exception e) {
            log.error("北向资金查询失败", e);
            return "错误: 北向资金查询失败 - " + e.getMessage();
        }
    }

    private String fetchNorthBound(String secid, String label, int days) {
        try {
            String url = "http://push2.eastmoney.com/api/qt/kamt.kline/get"
                    + "?secid=" + secid
                    + "&klt=101"
                    + "&lmt=" + days
                    + "&fields1=f1,f2,f3,f4"
                    + "&fields2=f51,f52,f53";

            JsonNode root = httpGet(url);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                return "  " + label + ": 数据暂不可用";
            }

            String name = data.path("name").asText(label);
            JsonNode klines = data.path("klines");
            if (!klines.isArray() || klines.isEmpty()) {
                return "  " + label + ": 暂无数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("  ").append(name).append("\n");
            sb.append(String.format("  %-14s %14s %14s%n", "日期", "当日净买入", "累计净买入"));
            sb.append("  ").append("─".repeat(44)).append("\n");

            for (JsonNode line : klines) {
                String[] parts = line.asText().split(",");
                if (parts.length < 3) continue;
                String date = parts[0];
                String dailyNet = fmtMoney(parts[1]);    // 当日净买入（元）
                String cumNet = fmtMoney(parts[2]);       // 累计净买入（元）

                sb.append(String.format("  %-14s %14s %14s%n", date, dailyNet, cumNet));
            }

            return sb.toString();

        } catch (Exception e) {
            log.warn("北向资金查询失败: secid={}", secid, e);
            return "  " + label + ": 查询失败 - " + e.getMessage();
        }
    }

    // ==================== 综合摘要（供 Agent 使用） ====================

    /**
     * 生成资金面综合摘要，供金融分析 Agent 直接使用。
     * <p>
     * 包含：板块资金排名 TOP5 + 北向资金最新动态。
     */
    public String getMoneyFlowSummary() {
        StringBuilder sb = new StringBuilder();

        // 行业板块 TOP5
        try {
            String url = "http://push2.eastmoney.com/api/qt/clist/get"
                    + "?pn=1&pz=5&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281"
                    + "&fltt=2&invt=2&fid=f62&fs=m:90+t:2"
                    + "&fields=" + SECTOR_FIELDS;

            JsonNode root = httpGet(url);
            JsonNode diffs = root.path("data").path("diff");
            if (diffs.isArray() && !diffs.isEmpty()) {
                sb.append("【行业板块资金流向 TOP5】\n");
                for (JsonNode item : diffs) {
                    String name = item.path("f14").asText("-");
                    String mainForce = fmtMoney(item.path("f62").asText());
                    String changePct = fmtNum(item.path("f3").asText()) + "%";
                    sb.append("  ").append(name).append(": 主力")
                            .append(mainForce).append(" 涨跌").append(changePct).append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.debug("资金面摘要-板块: {}", e.getMessage());
        }

        // 北向资金最新
        try {
            String url = "http://push2.eastmoney.com/api/qt/kamt.kline/get"
                    + "?secid=1.000001&klt=101&lmt=1"
                    + "&fields1=f1,f2,f3,f4&fields2=f51,f52,f53";
            JsonNode root = httpGet(url);
            JsonNode klines = root.path("data").path("klines");
            if (klines.isArray() && !klines.isEmpty()) {
                String[] parts = klines.get(0).asText().split(",");
                if (parts.length >= 2) {
                    sb.append("【北向资金最新】沪股通上一交易日净买入: ")
                            .append(fmtMoney(parts[1])).append("\n");
                }
            }
        } catch (Exception e) {
            log.debug("资金面摘要-北向: {}", e.getMessage());
        }

        return sb.toString();
    }

    // ==================== 工具方法 ====================

    /**
     * HTTP GET 并解析 JSON。
     */
    private JsonNode httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Referer", "https://quote.eastmoney.com");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestMethod("GET");

        java.io.InputStream is = conn.getInputStream();
        String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();
        conn.disconnect();

        return mapper.readTree(response);
    }

    /**
     * 将股票名称/代码解析为东方财富 secid。
     * 复用 StockService 的命名逻辑。
     */
    private String resolveToEastMoneySecid(String input) {
        if (input == null || input.isBlank()) return null;

        String trimmed = input.trim();

        // 常见名称映射
        Map<String, String> nameMap = Map.ofEntries(
                Map.entry("贵州茅台", "1.600519"), Map.entry("茅台", "1.600519"),
                Map.entry("宁德时代", "0.300750"),
                Map.entry("比亚迪", "0.002594"),
                Map.entry("中国平安", "1.601318"),
                Map.entry("招商银行", "1.600036"),
                Map.entry("五粮液", "0.000858"),
                Map.entry("隆基绿能", "1.601012"),
                Map.entry("中芯国际", "1.688981"),
                Map.entry("工商银行", "1.601398"),
                Map.entry("寒武纪", "1.688256"),
                Map.entry("海康威视", "0.002415"),
                Map.entry("美的集团", "0.000333"),
                Map.entry("格力电器", "0.000651"),
                Map.entry("中兴通讯", "0.000063"),
                Map.entry("药明康德", "1.603259"),
                Map.entry("长江电力", "1.600900"),
                Map.entry("中国石油", "1.601857"),
                Map.entry("中国移动", "1.600941"),
                Map.entry("万科", "0.000002"),
                Map.entry("保利发展", "1.600048"),
                Map.entry("中信证券", "1.600030"),
                Map.entry("东方财富", "0.300059"),
                Map.entry("阳光电源", "0.300274"),
                Map.entry("迈瑞医疗", "0.300760"),
                Map.entry("恒瑞医药", "1.600276"),
                Map.entry("伊利股份", "1.600887"),
                Map.entry("海尔智家", "1.600690"),
                Map.entry("三一重工", "1.600031")
        );

        String code = nameMap.get(trimmed);
        if (code != null) return code;

        // 已带 secid 格式
        if (trimmed.matches("^[01]\\.[0-9]{6}$")) return trimmed;

        // sh/sz/bj 前缀转 secid
        if (trimmed.matches("(?i)^sh[0-9]{6}$"))
            return "1." + trimmed.substring(2);
        if (trimmed.matches("(?i)^sz[0-9]{6}$"))
            return "0." + trimmed.substring(2);
        if (trimmed.matches("(?i)^bj[0-9]{6}$"))
            return "0." + trimmed.substring(2);

        // 纯 6 位数字
        if (trimmed.matches("^[0-9]{6}$")) {
            char first = trimmed.charAt(0);
            return switch (first) {
                case '6', '5' -> "1." + trimmed;
                case '0', '3', '1' -> "0." + trimmed;
                case '8', '4' -> "0." + trimmed;
                default -> null;
            };
        }

        return null;
    }

    /** 金额格式化（自动亿/万） */
    private static String fmtMoney(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw) || "-".equals(raw)) return "—";
        try {
            BigDecimal val = new BigDecimal(raw);
            BigDecimal absVal = val.abs();
            if (absVal.compareTo(new BigDecimal("100000000")) >= 0) {
                return val.divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP) + "亿";
            }
            if (absVal.compareTo(new BigDecimal("10000")) >= 0) {
                return val.divide(new BigDecimal("10000"), 1, RoundingMode.HALF_UP) + "万";
            }
            return val.longValue() + "元";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** 数字格式化（两位小数） */
    private static String fmtNum(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw) || "-".equals(raw)) return "—";
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).toString();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** 截断字符串 */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "—";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
