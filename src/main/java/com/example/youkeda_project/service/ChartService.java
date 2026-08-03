package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 通用图表生成服务。
 * 纯 Java2D 实现，无第三方依赖。
 * 支持折线图、K线图（含均线+成交量）、多品种对比图。
 */
@Service
public class ChartService {

    private static final Logger log = LoggerFactory.getLogger(ChartService.class);

    private static final int WIDTH = 800;
    private static final int HEIGHT = 520;
    private static final int PAD_LEFT = 80;
    private static final int PAD_RIGHT = 40;
    private static final int PAD_TOP = 80;
    private static final int PAD_BOTTOM = 80;

    private static final Color BG_COLOR = new Color(24, 24, 32);
    private static final Color GRID_COLOR = new Color(50, 50, 65);
    private static final Color TEXT_COLOR = new Color(200, 200, 210);
    private static final Color TITLE_COLOR = new Color(255, 255, 255);
    private static final Color LINE_COLOR = new Color(0, 180, 255);
    private static final Color FILL_COLOR_UP = new Color(0, 180, 255, 40);
    private static final Color FILL_COLOR_DOWN = new Color(255, 80, 80, 40);
    private static final Color UP_COLOR = new Color(255, 80, 80);
    private static final Color DOWN_COLOR = new Color(0, 200, 100);
    private static final Color LABEL_COLOR = new Color(160, 160, 175);
    private static final Color VOLUME_UP_COLOR = new Color(255, 80, 80, 100);
    private static final Color VOLUME_DOWN_COLOR = new Color(0, 200, 100, 100);

    // MA 均线颜色
    private static final Color MA5_COLOR = new Color(255, 200, 0);
    private static final Color MA10_COLOR = new Color(255, 100, 200);
    private static final Color MA20_COLOR = new Color(0, 255, 200);
    private static final Color MA60_COLOR = new Color(180, 100, 255);

    // 多品种对比颜色
    private static final Color[] COMPARE_COLORS = {
        new Color(0, 180, 255),
        new Color(255, 100, 100),
        new Color(0, 220, 100),
        new Color(255, 200, 0),
        new Color(200, 100, 255)
    };

    private static Font fontTitle;
    private static Font fontLabel;
    private static Font fontSmall;

    static {
        try {
            Font baseFont = Font.createFont(Font.TRUETYPE_FONT,
                new java.io.File("C:\\Windows\\Fonts\\msyh.ttc"));
            fontTitle = baseFont.deriveFont(Font.BOLD, 22f);
            fontLabel = baseFont.deriveFont(Font.PLAIN, 13f);
            fontSmall = baseFont.deriveFont(Font.PLAIN, 11f);
        } catch (Exception e) {
            fontTitle = new Font("SansSerif", Font.BOLD, 22);
            fontLabel = new Font("SansSerif", Font.PLAIN, 13);
            fontSmall = new Font("SansSerif", Font.PLAIN, 11);
        }
    }

    // ==================== 数据点定义 ====================

    /** 折线图数据点 */
    public record ChartPoint(String date, BigDecimal value, String changePct) {}

    /** K线数据点 (OHLC + 成交量) */
    public record CandlestickPoint(String date, BigDecimal open, BigDecimal high,
                                   BigDecimal low, BigDecimal close, long volume,
                                   String changePct) {}

    /** 多品种对比数据 */
    public record SeriesData(String name, List<ChartPoint> data) {}

    // ==================== 1. 折线图（自动带均线） ====================

    /**
     * 生成折线图。数据点 >= 20 时自动添加 MA5/10/20 均线。
     */
    public byte[] generateChart(String title, String subtitle, List<ChartPoint> data) {
        List<Integer> maPeriods = data.size() >= 20 ? List.of(5, 10, 20) : null;
        return generateLineChartInternal(title, subtitle, data, maPeriods);
    }

    /**
     * 生成带指定均线的折线图。
     */
    public byte[] generateChartWithMA(String title, String subtitle, List<ChartPoint> data,
                                      List<Integer> maPeriods) {
        return generateLineChartInternal(title, subtitle, data, maPeriods);
    }

    private byte[] generateLineChartInternal(String title, String subtitle, List<ChartPoint> data,
                                              List<Integer> maPeriods) {
        if (data == null || data.size() < 2) return null;

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        int plotX = PAD_LEFT;
        int plotY = PAD_TOP;
        int plotW = WIDTH - PAD_LEFT - PAD_RIGHT;
        int plotH = HEIGHT - PAD_TOP - PAD_BOTTOM;

        BigDecimal[] values = new BigDecimal[data.size()];
        BigDecimal minVal = new BigDecimal("999999999");
        BigDecimal maxVal = new BigDecimal("0");
        for (int i = 0; i < data.size(); i++) {
            values[i] = data.get(i).value();
            if (values[i].compareTo(minVal) < 0) minVal = values[i];
            if (values[i].compareTo(maxVal) > 0) maxVal = values[i];
        }

        // 计算MA值并扩展范围
        List<MAData> maDataList = new ArrayList<>();
        if (maPeriods != null) {
            for (int period : maPeriods) {
                BigDecimal[] maValues = calcMA(values, period);
                Color color = getMAColor(period);
                maDataList.add(new MAData(period, maValues, color));
                for (BigDecimal v : maValues) {
                    if (v != null) {
                        if (v.compareTo(minVal) < 0) minVal = v;
                        if (v.compareTo(maxVal) > 0) maxVal = v;
                    }
                }
            }
        }

        BigDecimal range = maxVal.subtract(minVal);
        if (range.compareTo(BigDecimal.ZERO) == 0) range = new BigDecimal("0.01");
        BigDecimal padding = range.multiply(new BigDecimal("0.1"));
        minVal = minVal.subtract(padding);
        maxVal = maxVal.add(padding);
        range = maxVal.subtract(minVal);

        drawGrid(g, plotX, plotY, plotW, plotH, minVal, maxVal);
        drawLine(g, data, values, plotX, plotY, plotW, plotH, minVal, maxVal);

        // 绘制MA均线
        for (MAData ma : maDataList) {
            drawMALine(g, ma.values(), ma.color(), plotX, plotY, plotW, plotH, minVal, maxVal, data.size());
        }

        drawTitle(g, title, subtitle);
        drawDateLabels(g, data.stream().map(ChartPoint::date).toList(), plotX, plotY, plotW, plotH, data.size());
        drawLineLegend(g, maDataList);

        g.dispose();
        return toBytes(image, "折线图");
    }

    // ==================== 2. K线蜡烛图（含均线 + 成交量） ====================

    /**
     * 生成K线蜡烛图，包含 MA5/10/20 均线和底部成交量柱状图。
     */
    public byte[] generateCandlestickChart(String title, String subtitle, List<CandlestickPoint> data) {
        if (data == null || data.size() < 2) return null;

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        int plotX = PAD_LEFT;
        int plotY = PAD_TOP;
        int plotW = WIDTH - PAD_LEFT - PAD_RIGHT;
        int totalH = HEIGHT - PAD_TOP - PAD_BOTTOM;

        // K线区域占80%，成交量占20%
        int candleH = (int) (totalH * 0.78);
        int volumeH = (int) (totalH * 0.18);
        int volumeY = plotY + candleH + (totalH - candleH - volumeH) / 2;

        // 计算价格范围
        BigDecimal minPrice = new BigDecimal("999999999");
        BigDecimal maxPrice = new BigDecimal("0");
        for (CandlestickPoint p : data) {
            if (p.low().compareTo(minPrice) < 0) minPrice = p.low();
            if (p.high().compareTo(maxPrice) > 0) maxPrice = p.high();
        }

        // 计算MA并扩展价格范围
        BigDecimal[] closes = data.stream().map(CandlestickPoint::close).toArray(BigDecimal[]::new);
        List<MAData> maDataList = new ArrayList<>();
        for (int period : List.of(5, 10, 20)) {
            BigDecimal[] maValues = calcMA(closes, period);
            Color color = getMAColor(period);
            maDataList.add(new MAData(period, maValues, color));
            for (BigDecimal v : maValues) {
                if (v != null) {
                    if (v.compareTo(minPrice) < 0) minPrice = v;
                    if (v.compareTo(maxPrice) > 0) maxPrice = v;
                }
            }
        }

        BigDecimal priceRange = maxPrice.subtract(minPrice);
        if (priceRange.compareTo(BigDecimal.ZERO) == 0) priceRange = new BigDecimal("0.01");
        BigDecimal pricePadding = priceRange.multiply(new BigDecimal("0.08"));
        minPrice = minPrice.subtract(pricePadding);
        maxPrice = maxPrice.add(pricePadding);
        priceRange = maxPrice.subtract(minPrice);

        // 计算成交量范围
        long maxVol = 0;
        for (CandlestickPoint p : data) {
            if (p.volume() > maxVol) maxVol = p.volume();
        }
        if (maxVol == 0) maxVol = 1;

        drawGrid(g, plotX, plotY, plotW, candleH, minPrice, maxPrice);

        int n = data.size();
        int gap = plotW / n;
        int candleW = Math.max(3, Math.min(18, gap - 2));

        // 绘制K线
        for (int i = 0; i < n; i++) {
            CandlestickPoint p = data.get(i);
            int cx = plotX + gap * i + gap / 2;

            int yOpen = plotY + candleH - (int) (candleH * p.open().subtract(minPrice).doubleValue() / priceRange.doubleValue());
            int yHigh = plotY + candleH - (int) (candleH * p.high().subtract(minPrice).doubleValue() / priceRange.doubleValue());
            int yLow = plotY + candleH - (int) (candleH * p.low().subtract(minPrice).doubleValue() / priceRange.doubleValue());
            int yClose = plotY + candleH - (int) (candleH * p.close().subtract(minPrice).doubleValue() / priceRange.doubleValue());

            boolean isUp = p.close().compareTo(p.open()) >= 0;
            Color bodyColor = isUp ? UP_COLOR : DOWN_COLOR;

            // 上下影线
            g.setColor(bodyColor);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(cx, yHigh, cx, yLow);

            // 实体
            int bodyTop = Math.min(yOpen, yClose);
            int bodyH = Math.max(Math.abs(yOpen - yClose), 1);
            g.setColor(bodyColor);
            g.fillRect(cx - candleW / 2, bodyTop, candleW, bodyH);
        }

        // 绘制MA均线
        for (MAData ma : maDataList) {
            drawMALine(g, ma.values(), ma.color(), plotX, plotY, plotW, candleH, minPrice, maxPrice, n);
        }

        // 绘制成交量柱状图
        for (int i = 0; i < n; i++) {
            CandlestickPoint p = data.get(i);
            int cx = plotX + gap * i + gap / 2;
            int volBarH = (int) (volumeH * p.volume() / (double) maxVol);
            boolean isUp = p.close().compareTo(p.open()) >= 0;
            g.setColor(isUp ? VOLUME_UP_COLOR : VOLUME_DOWN_COLOR);
            g.fillRect(cx - candleW / 2, volumeY + volumeH - volBarH, candleW, volBarH);
        }

        drawTitle(g, title, subtitle);
        drawDateLabels(g, data.stream().map(CandlestickPoint::date).toList(), plotX, plotY, plotW, totalH, n);
        drawCandlestickLegend(g, maDataList);

        g.dispose();
        return toBytes(image, "K线图");
    }

    // ==================== 3. 多品种对比图 ====================

    /**
     * 生成多品种对比折线图（归一化为涨跌幅）。
     */
    public byte[] generateComparisonChart(String title, String subtitle, List<SeriesData> seriesList) {
        if (seriesList == null || seriesList.isEmpty()) return null;

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        int plotX = PAD_LEFT;
        int plotY = PAD_TOP;
        int plotW = WIDTH - PAD_LEFT - PAD_RIGHT;
        int plotH = HEIGHT - PAD_TOP - PAD_BOTTOM;

        List<List<BigDecimal>> allNormalized = new ArrayList<>();
        BigDecimal minVal = BigDecimal.ZERO;
        BigDecimal maxVal = BigDecimal.ZERO;

        for (SeriesData series : seriesList) {
            List<BigDecimal> normalized = normalizeToPercent(series.data());
            allNormalized.add(normalized);
            for (BigDecimal v : normalized) {
                if (v.compareTo(minVal) < 0) minVal = v;
                if (v.compareTo(maxVal) > 0) maxVal = v;
            }
        }

        BigDecimal range = maxVal.subtract(minVal);
        if (range.compareTo(BigDecimal.ZERO) == 0) range = new BigDecimal("0.01");
        BigDecimal padding = range.multiply(new BigDecimal("0.15"));
        minVal = minVal.subtract(padding);
        maxVal = maxVal.add(padding);
        range = maxVal.subtract(minVal);

        drawGrid(g, plotX, plotY, plotW, plotH, minVal, maxVal);
        drawZeroLine(g, plotX, plotY, plotW, plotH, minVal, maxVal);

        int maxLen = 0;
        for (SeriesData s : seriesList) {
            if (s.data().size() > maxLen) maxLen = s.data().size();
        }

        for (int si = 0; si < seriesList.size(); si++) {
            SeriesData series = seriesList.get(si);
            List<BigDecimal> normalized = allNormalized.get(si);
            Color color = COMPARE_COLORS[si % COMPARE_COLORS.length];
            int n = normalized.size();

            int[] px = new int[n];
            int[] py = new int[n];
            for (int i = 0; i < n; i++) {
                px[i] = plotX + (int) (plotW * i / (double) (maxLen - 1));
                py[i] = plotY + plotH - (int) (plotH * normalized.get(i).subtract(minVal).doubleValue() / range.doubleValue());
            }

            g.setColor(color);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < n - 1; i++) {
                g.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
            }

            g.setFont(fontSmall);
            String label = series.name();
            g.drawString(label, px[n - 1] + 5, py[n - 1] + 4);
        }

        drawTitle(g, title, subtitle);
        drawComparisonLegend(g, seriesList);

        // 找最长序列的日期标签
        List<String> dates = List.of();
        int maxDataLen = 0;
        for (SeriesData s : seriesList) {
            if (s.data().size() > maxDataLen) {
                maxDataLen = s.data().size();
                dates = s.data().stream().map(ChartPoint::date).toList();
            }
        }
        drawDateLabels(g, dates, plotX, plotY, plotW, plotH, maxDataLen);

        g.dispose();
        return toBytes(image, "对比图");
    }

    // ==================== 内部工具方法 ====================

    private byte[] toBytes(BufferedImage image, String chartType) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] bytes = baos.toByteArray();
            log.info("{}生成成功: {} bytes", chartType, bytes.length);
            return bytes;
        } catch (Exception e) {
            log.error("{}输出失败: {}", chartType, e.getMessage());
            return null;
        }
    }

    private BigDecimal[] calcMA(BigDecimal[] values, int period) {
        int n = values.length;
        BigDecimal[] ma = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            if (i < period - 1) {
                ma[i] = null;
            } else {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - period + 1; j <= i; j++) {
                    sum = sum.add(values[j]);
                }
                ma[i] = sum.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
            }
        }
        return ma;
    }

    private List<BigDecimal> normalizeToPercent(List<ChartPoint> data) {
        List<BigDecimal> result = new ArrayList<>();
        if (data.isEmpty()) return result;
        BigDecimal base = data.get(0).value();
        if (base.compareTo(BigDecimal.ZERO) == 0) base = BigDecimal.ONE;
        for (ChartPoint p : data) {
            BigDecimal pct = p.value().subtract(base)
                .divide(base, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            result.add(pct);
        }
        return result;
    }

    private Color getMAColor(int period) {
        return switch (period) {
            case 5 -> MA5_COLOR;
            case 10 -> MA10_COLOR;
            case 20 -> MA20_COLOR;
            case 60 -> MA60_COLOR;
            default -> MA5_COLOR;
        };
    }

    // ==================== 绘制方法 ====================

    private void drawGrid(Graphics2D g, int x, int y, int w, int h,
                          BigDecimal minVal, BigDecimal maxVal) {
        g.setColor(GRID_COLOR);
        g.setStroke(new BasicStroke(0.5f));
        int gridCount = 5;
        for (int i = 0; i <= gridCount; i++) {
            int py = y + (int) (h * i / (double) gridCount);
            g.drawLine(x, py, x + w, py);

            BigDecimal val = maxVal.subtract(maxVal.subtract(minVal)
                .multiply(new BigDecimal(String.valueOf(i)))
                .divide(new BigDecimal(String.valueOf(gridCount)), 4, RoundingMode.HALF_UP));
            g.setColor(LABEL_COLOR);
            g.setFont(fontSmall);
            String label = val.setScale(2, RoundingMode.HALF_UP).toString();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, x + w + 5, py + fm.getAscent() / 2 - 2);
            g.setColor(GRID_COLOR);
        }
    }

    private void drawZeroLine(Graphics2D g, int x, int y, int w, int h,
                              BigDecimal minVal, BigDecimal maxVal) {
        BigDecimal range = maxVal.subtract(minVal);
        if (range.compareTo(BigDecimal.ZERO) == 0) return;
        int zeroY = y + h - (int) (h * BigDecimal.ZERO.subtract(minVal).doubleValue() / range.doubleValue());
        if (zeroY >= y && zeroY <= y + h) {
            g.setColor(new Color(100, 100, 120));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{5f, 5f}, 0f));
            g.drawLine(x, zeroY, x + w, zeroY);
        }
    }

    private void drawLine(Graphics2D g, List<ChartPoint> data, BigDecimal[] values,
                          int x, int y, int w, int h, BigDecimal minVal, BigDecimal maxVal) {
        int n = data.size();
        BigDecimal range = maxVal.subtract(minVal);
        if (range.compareTo(BigDecimal.ZERO) == 0) return;

        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = x + (int) (w * i / (double) (n - 1));
            py[i] = y + h - (int) (h * values[i].subtract(minVal).doubleValue() / range.doubleValue());
        }

        boolean isUp = values[n - 1].compareTo(values[0]) >= 0;
        Color fillColor = isUp ? FILL_COLOR_UP : FILL_COLOR_DOWN;

        Polygon fillArea = new Polygon();
        fillArea.addPoint(px[0], y + h);
        for (int i = 0; i < n; i++) fillArea.addPoint(px[i], py[i]);
        fillArea.addPoint(px[n - 1], y + h);
        g.setColor(fillColor);
        g.fillPolygon(fillArea);

        g.setColor(LINE_COLOR);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < n - 1; i++) {
            g.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
        }

        for (int i = 0; i < n; i++) {
            boolean dayUp = false;
            if (data.get(i).changePct() != null) {
                try {
                    dayUp = new BigDecimal(data.get(i).changePct().trim()).compareTo(BigDecimal.ZERO) >= 0;
                } catch (NumberFormatException ignored) {}
            }
            Color dotColor = dayUp ? UP_COLOR : DOWN_COLOR;
            g.setColor(dotColor);
            g.fillOval(px[i] - 4, py[i] - 4, 8, 8);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1f));
            g.drawOval(px[i] - 4, py[i] - 4, 8, 8);
        }

        g.setColor(TEXT_COLOR);
        g.setFont(fontSmall);
        FontMetrics fm = g.getFontMetrics();
        String firstVal = values[0].setScale(2, RoundingMode.HALF_UP).toString();
        g.drawString(firstVal, px[0] - fm.stringWidth(firstVal) - 8, py[0] + 4);
        String lastVal = values[n - 1].setScale(2, RoundingMode.HALF_UP).toString();
        g.drawString(lastVal, px[n - 1] + 10, py[n - 1] + 4);
    }

    private void drawMALine(Graphics2D g, BigDecimal[] maValues, Color color,
                            int x, int y, int w, int h, BigDecimal minVal, BigDecimal maxVal, int n) {
        BigDecimal range = maxVal.subtract(minVal);
        if (range.compareTo(BigDecimal.ZERO) == 0) return;

        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        boolean drawing = false;
        for (int i = 0; i < n; i++) {
            if (maValues[i] == null) {
                drawing = false;
                continue;
            }
            int px = x + (int) (w * i / (double) (n - 1));
            int py = y + h - (int) (h * maValues[i].subtract(minVal).doubleValue() / range.doubleValue());
            if (!drawing) {
                g.setColor(color);
                drawing = true;
            }
            if (i > 0 && maValues[i - 1] != null) {
                int prevPx = x + (int) (w * (i - 1) / (double) (n - 1));
                int prevPy = y + h - (int) (h * maValues[i - 1].subtract(minVal).doubleValue() / range.doubleValue());
                g.drawLine(prevPx, prevPy, px, py);
            }
        }
    }

    private void drawTitle(Graphics2D g, String title, String subtitle) {
        g.setColor(TITLE_COLOR);
        g.setFont(fontTitle);
        g.drawString(title, PAD_LEFT, PAD_TOP - 35);

        g.setFont(fontLabel);
        g.setColor(LINE_COLOR);
        g.drawString(subtitle, PAD_LEFT, PAD_TOP - 12);
    }

    private void drawDateLabels(Graphics2D g, List<String> dates,
                                int x, int y, int w, int h, int totalLen) {
        g.setColor(LABEL_COLOR);
        g.setFont(fontSmall);
        FontMetrics fm = g.getFontMetrics();
        int n = dates.size();
        int maxLabels = Math.min(6, n);
        for (int i = 0; i < maxLabels; i++) {
            int idx = (int) (i * (n - 1) / (double) (maxLabels - 1));
            int px = x + (int) (w * idx / (double) (totalLen - 1));
            String date = dates.get(idx);
            if (date.length() >= 10) date = date.substring(5, 10);
            int labelW = fm.stringWidth(date);
            g.drawString(date, px - labelW / 2, y + h + 20);
        }
    }

    private void drawLineLegend(Graphics2D g, List<MAData> maDataList) {
        int x = WIDTH - PAD_RIGHT - 160;
        int y = PAD_TOP - 35;
        g.setFont(fontSmall);

        g.setColor(UP_COLOR);
        g.fillOval(x, y, 8, 8);
        g.drawString("涨", x + 12, y + 8);
        g.setColor(DOWN_COLOR);
        g.fillOval(x + 40, y, 8, 8);
        g.drawString("跌", x + 52, y + 8);

        if (!maDataList.isEmpty()) {
            int mx = x + 90;
            for (MAData ma : maDataList) {
                g.setColor(ma.color());
                g.fillRect(mx, y + 2, 16, 3);
                g.drawString("MA" + ma.period(), mx + 20, y + 8);
                mx += g.getFontMetrics().stringWidth("MA" + ma.period()) + 30;
            }
        }
    }

    private void drawCandlestickLegend(Graphics2D g, List<MAData> maDataList) {
        int x = WIDTH - PAD_RIGHT - 200;
        int y = PAD_TOP - 35;
        g.setFont(fontSmall);

        g.setColor(UP_COLOR);
        g.fillRect(x, y, 12, 12);
        g.drawString("阳线", x + 16, y + 10);
        g.setColor(DOWN_COLOR);
        g.fillRect(x + 55, y, 12, 12);
        g.drawString("阴线", x + 71, y + 10);

        int mx = x + 120;
        for (MAData ma : maDataList) {
            g.setColor(ma.color());
            g.fillRect(mx, y + 4, 16, 3);
            g.drawString("MA" + ma.period(), mx + 20, y + 10);
            mx += g.getFontMetrics().stringWidth("MA" + ma.period()) + 25;
        }
    }

    private void drawComparisonLegend(Graphics2D g, List<SeriesData> seriesList) {
        int x = PAD_LEFT;
        int y = PAD_TOP - 35;
        g.setFont(fontSmall);
        FontMetrics fm = g.getFontMetrics();

        int mx = x;
        for (int i = 0; i < seriesList.size(); i++) {
            Color color = COMPARE_COLORS[i % COMPARE_COLORS.length];
            g.setColor(color);
            g.fillRect(mx, y + 2, 16, 3);
            g.drawString(seriesList.get(i).name(), mx + 20, y + 8);
            mx += fm.stringWidth(seriesList.get(i).name()) + 35;
        }
    }

    /** MA 数据 */
    private record MAData(int period, BigDecimal[] values, Color color) {}
}
