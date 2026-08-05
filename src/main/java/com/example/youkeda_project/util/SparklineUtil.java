package com.example.youkeda_project.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Unicode 迷你走势图（火花图）工具。
 * <p>
 * 将价格序列映射为 ▁▂▃▄▅▆▇█ 方块字符，
 * 在纯文本环境中直观展示价格趋势方向。
 */
public final class SparklineUtil {

    /** 8 级方块字符，从低到高 */
    private static final char[] BLOCKS = { '▁', '▂', '▃', '▄', '▅', '▆', '▇', '█' };

    /** 默认火花图宽度（字符数） */
    private static final int DEFAULT_WIDTH = 8;

    /** 最近多少天用于计算涨跌标签 */
    private static final int LABEL_DAYS = 5;

    private SparklineUtil() {}

    /**
     * 从 BigDecimal 列表生成火花图。
     *
     * @param values 价格序列（按时间顺序，最后一个是最新的）
     * @return 火花图字符串，如 "▃▄▅▄▃▂▃▄"
     */
    public static String generate(List<BigDecimal> values) {
        return generate(values, DEFAULT_WIDTH);
    }

    /**
     * 从 BigDecimal 列表生成指定宽度的火花图。
     */
    public static String generate(List<BigDecimal> values, int width) {
        if (values == null || values.isEmpty()) return "";

        // 如果数据点多于宽度，压缩采样
        List<BigDecimal> sampled = values.size() > width
                ? sample(values, width)
                : values;

        BigDecimal min = sampled.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = sampled.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal range = max.subtract(min);

        StringBuilder sb = new StringBuilder();
        for (BigDecimal v : sampled) {
            if (range.compareTo(BigDecimal.ZERO) == 0) {
                // 所有值相同 → 用中间高度
                sb.append(BLOCKS[3]);
            } else {
                int idx = v.subtract(min)
                        .multiply(BigDecimal.valueOf(BLOCKS.length - 1))
                        .divide(range, 0, RoundingMode.HALF_UP)
                        .intValue();
                idx = Math.max(0, Math.min(BLOCKS.length - 1, idx));
                sb.append(BLOCKS[idx]);
            }
        }
        return sb.toString();
    }

    /**
     * 从 double 数组生成火花图。
     */
    public static String generate(double[] values) {
        if (values == null || values.length == 0) return "";
        List<BigDecimal> list = new java.util.ArrayList<>(values.length);
        for (double v : values) list.add(BigDecimal.valueOf(v));
        return generate(list);
    }

    // ==================== 内部方法 ====================

    /**
     * 对数据点做均匀采样，保留首尾。
     */
    private static List<BigDecimal> sample(List<BigDecimal> values, int targetSize) {
        if (values.size() <= targetSize) return values;
        List<BigDecimal> result = new java.util.ArrayList<>(targetSize);
        result.add(values.get(0)); // 保留第一个
        double step = (double) (values.size() - 2) / (targetSize - 2);
        for (int i = 1; i < targetSize - 1; i++) {
            int idx = 1 + (int) Math.round(i * step);
            result.add(values.get(Math.min(idx, values.size() - 2)));
        }
        result.add(values.get(values.size() - 1)); // 保留最后一个
        return result;
    }
}
