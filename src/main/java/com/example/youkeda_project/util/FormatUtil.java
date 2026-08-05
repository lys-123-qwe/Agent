package com.example.youkeda_project.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 行情格式化通用工具。 */
public final class FormatUtil {

    private FormatUtil() {}

    /** 涨跌计算结果：change 涨跌额, changePct 涨跌幅(百分比), direction 涨跌表情, sign 正负号, computed 是否已计算 */
    public record ChangeInfo(BigDecimal change, BigDecimal changePct, String direction, String sign, boolean computed) {}

    /** 计算并格式化涨跌：prevClose 有效(>0)时计算涨跌额与涨跌幅，否则涨跌额为 0、方向默认 📈 */
    public static ChangeInfo calcChange(BigDecimal price, String prevClose) {
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal changePct = BigDecimal.ZERO;
        String direction = "📈";
        String sign = "";
        boolean computed = false;
        if (prevClose != null && !prevClose.isBlank()) {
            BigDecimal prev = new BigDecimal(prevClose.trim());
            if (prev.compareTo(BigDecimal.ZERO) > 0) {
                change = price.subtract(prev);
                changePct = changePct(change, prev, 4);
                direction = direction(change);
                sign = sign(change);
                computed = true;
            }
        }
        return new ChangeInfo(change, changePct, direction, sign, computed);
    }

    /** 计算并格式化涨跌（始终计算涨跌额）：用于昨收恒有效的场景，pctScale 为涨跌幅除法精度 */
    public static ChangeInfo calcChange(BigDecimal price, BigDecimal prevClose, int pctScale) {
        BigDecimal change = price.subtract(prevClose);
        return new ChangeInfo(change, changePct(change, prevClose, pctScale),
                direction(change), sign(change), prevClose.compareTo(BigDecimal.ZERO) > 0);
    }

    /** 计算涨跌幅(百分比)；prevClose<=0 时为 0 */
    public static BigDecimal changePct(BigDecimal change, BigDecimal prevClose, int scale) {
        if (prevClose.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return change.divide(prevClose, scale, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /** 涨跌方向表情：涨/平 📈，跌 📉 */
    public static String direction(BigDecimal change) {
        return change.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
    }

    /** 涨跌正负号：涨/平 "+"，跌 "" */
    public static String sign(BigDecimal change) {
        return change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
    }

    /** 判断行情日期是否为今天（yyyy-MM-dd）。 */
    public static boolean isToday(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return false;
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return dateStr.trim().contains(today);
    }

    /** 组装行情日期时间串；非今日数据附加 ⚠️ 标注。 */
    public static String formatQuoteDate(String dateStr, String timeStr) {
        String ts = dateStr + (timeStr != null && !timeStr.isBlank() ? " " + timeStr : "");
        if (isToday(dateStr)) {
            return ts;
        }
        return ts + "（⚠️非今日数据）";
    }
}
