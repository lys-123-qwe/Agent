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

    /** 从行情文本首行提取股票名称(去掉 emoji 与括号代码),未找到返回 null. */
    public static String extractName(String quote) {
        if (quote == null) return null;
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
}
