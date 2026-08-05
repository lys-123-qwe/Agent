package com.example.youkeda_project.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从行情文本中解析股票代码/名称/字段.
 * <p>行情文本格式由 {@code StockService} 生成,例如:
 * <pre>📈 贵州茅台（sh600519）
 * 当前价: 1680.00
 * 涨跌幅: +1.20%</pre>
 */
public final class StockQuoteParser {

    private static final Pattern CODE_PATTERN =
        Pattern.compile("[（(]((?:sh|sz|hk|gb_)\\w+)[）)]");

    private StockQuoteParser() {}

    /** 从行情文本中提取股票代码(小写),未找到返回 null. */
    public static String extractCode(String quote) {
        if (quote == null) return null;
        for (String line : quote.split("\n")) {
            Matcher m = CODE_PATTERN.matcher(line);
            if (m.find()) {
                return m.group(1).toLowerCase();
            }
        }
        return null;
    }

    /** 从行情文本中提取股票名称(去掉 emoji 与括号代码),未找到返回 null.
     *  跳过开头的 sparkline 前缀行(如"近30日走势"),避免把火花图标签当成股票名. */
    public static String extractName(String quote) {
        if (quote == null) return null;
        for (String line : quote.split("\n")) {
            // 名称行包含股票代码括号(如"📊 贵州茅台（sh600519）"),sparkline行不含
            if (CODE_PATTERN.matcher(line).find()) {
                return line.replaceAll("^[📈📉📊]", "")
                    .replaceAll("[（(]\\w+[）)].*", "")
                    .trim();
            }
        }
        // fallback: 首行(去掉emoji与括号代码)
        String first = quote.split("\n")[0].trim();
        return first.replaceAll("^[📈📉📊]", "")
            .replaceAll("[（(]\\w+[）)].*", "")
            .trim();
    }

    /** 从行情文本中提取指定字段的值,未找到返回 null. */
    public static String extractField(String quote, String field) {
        if (quote == null) return null;
        for (String line : quote.split("\n")) {
            if (line.contains(field)) {
                String val = line.substring(line.indexOf(":") + 1).trim();
                return val.length() > 15 ? val.substring(0, 15) : val;
            }
        }
        return null;
    }

    /** 提取响应字符串中第一对双引号之间的内容并按分隔符切分（新浪/腾讯行情格式）。无引号或空内容返回 null。 */
    public static String[] extractQuotedFields(String resp, String splitRe) {
        int eqIdx = resp.indexOf('"');
        if (eqIdx < 0) return null;
        String quoted = resp.substring(eqIdx + 1, resp.lastIndexOf('"'));
        if (quoted.isEmpty()) return null;
        return quoted.split(splitRe, -1);
    }
}
