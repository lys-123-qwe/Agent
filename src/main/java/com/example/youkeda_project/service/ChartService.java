package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ChartService {

    private static final Logger log = LoggerFactory.getLogger(ChartService.class);

    public static class ChartPoint {
        private final String label;
        private final BigDecimal value;
        private final String changePct;
        public ChartPoint(String label, BigDecimal value, String changePct) {
            this.label = label; this.value = value; this.changePct = changePct;
        }
        public String getLabel() { return label; }
        public BigDecimal getValue() { return value; }
        public String getChangePct() { return changePct; }
    }

    public static class CandlestickPoint {
        private final String day;
        private final BigDecimal open;
        private final BigDecimal high;
        private final BigDecimal low;
        private final BigDecimal close;
        private final long volume;
        private final String changeStr;
        public CandlestickPoint(String day, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume, String changeStr) {
            this.day = day; this.open = open; this.high = high;
            this.low = low; this.close = close; this.volume = volume; this.changeStr = changeStr;
        }
        public String getDay() { return day; }
        public BigDecimal getOpen() { return open; }
        public BigDecimal getHigh() { return high; }
        public BigDecimal getLow() { return low; }
        public BigDecimal getClose() { return close; }
        public long getVolume() { return volume; }
        public String getChangeStr() { return changeStr; }
    }

    public record OverlaySeries(String name, Color color, List<ChartPoint> points) {}

    public byte[] generateOverlayChart(String title, List<OverlaySeries> series) {
        if (series == null || series.size() < 1) return null;
        try {
            BufferedImage img = new BufferedImage(800, 450, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 800, 450);

            // 找出所有系列的数据范围（以百分比变化为基准归一化）
            double min = 0, max = 0;
            for (var s : series) {
                double firstClose = s.points().get(0).getValue().doubleValue();
                for (var p : s.points()) {
                    double pct = (p.getValue().doubleValue() - firstClose) / firstClose * 100;
                    if (pct < min) min = pct;
                    if (pct > max) max = pct;
                }
            }
            double pad = (max - min) * 0.1; if (pad == 0) pad = 1;
            min -= pad; max += pad;

            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            g.setColor(Color.DARK_GRAY);
            g.drawString(title, 20, 25);

            int w = 700, h = 330, x0 = 80, y0 = 50;
            int maxLen = series.stream().mapToInt(s -> s.points().size()).max().orElse(0);

            Color[] lineColors = {new Color(0xE53935), new Color(0x2196F3), new Color(0x4CAF50), new Color(0xFF9800)};

            for (int si = 0; si < series.size(); si++) {
                var s = series.get(si);
                double base = s.points().get(0).getValue().doubleValue();
                Color lineColor = lineColors[si % lineColors.length];
                g.setColor(lineColor);
                g.setStroke(new BasicStroke(2f));

                for (int i = 1; i < s.points().size(); i++) {
                    double v1 = (s.points().get(i - 1).getValue().doubleValue() - base) / base * 100;
                    double v2 = (s.points().get(i).getValue().doubleValue() - base) / base * 100;
                    int x1 = x0 + (int) ((i - 1) * (double) w / maxLen);
                    int x2 = x0 + (int) (i * (double) w / maxLen);
                    int y1 = y0 + (int) ((max - v1) / (max - min) * h);
                    int y2 = y0 + (int) ((max - v2) / (max - min) * h);
                    g.draw(new Line2D.Double(x1, y1, x2, y2));
                }
            }

            // 图例
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            int legX = 600, legY = 40;
            for (int si = 0; si < series.size(); si++) {
                var s = series.get(si);
                Color c = lineColors[si % lineColors.length];
                g.setColor(c);
                g.fillRect(legX, legY + si * 20, 12, 12);
                g.setColor(Color.DARK_GRAY);
                g.drawString(s.name(), legX + 18, legY + si * 20 + 11);
            }

            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("生成叠加图表失败", e);
            return null;
        }
    }

    public byte[] generateChart(String title, String subtitle, List<ChartPoint> points) {
        if (points == null || points.size() < 2) return null;
        try {
            BufferedImage img = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 800, 400);

            double min = points.stream().mapToDouble(p -> p.getValue().doubleValue()).min().orElse(0);
            double max = points.stream().mapToDouble(p -> p.getValue().doubleValue()).max().orElse(100);
            double pad = (max - min) * 0.1; if (pad == 0) pad = 1;
            min -= pad; max += pad;

            g.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
            g.setColor(Color.DARK_GRAY);
            g.drawString(title, 20, 30);
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));

            int w = 760, h = 320, x0 = 40, y0 = 60;
            for (int i = 1; i < points.size(); i++) {
                var p1 = points.get(i - 1);
                var p2 = points.get(i);
                int x1 = x0 + (int) ((i - 1) * (double) w / (points.size() - 1));
                int x2 = x0 + (int) (i * (double) w / (points.size() - 1));
                int y1 = y0 + (int) ((max - p1.getValue().doubleValue()) / (max - min) * h);
                int y2 = y0 + (int) ((max - p2.getValue().doubleValue()) / (max - min) * h);
                Color c = p2.getValue().compareTo(p1.getValue()) >= 0 ? new Color(0xE53935) : new Color(0x4CAF50);
                g.setColor(c);
                g.draw(new Line2D.Double(x1, y1, x2, y2));
            }

            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("生成图表失败", e);
            return null;
        }
    }

    public byte[] generateCandlestickChart(String title, String subtitle, List<CandlestickPoint> points) {
        return generateKLineChartFromPoints(title, points);
    }

    private byte[] generateKLineChartFromPoints(String title, List<CandlestickPoint> points) {
        if (points == null || points.isEmpty()) return null;
        int count = Math.min(points.size(), 60);
        points = points.subList(points.size() - count, points.size());

        BufferedImage img = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 500);

        double maxPrice = points.stream().mapToDouble(p -> p.getHigh().doubleValue()).max().orElse(100);
        double minPrice = points.stream().mapToDouble(p -> p.getLow().doubleValue()).min().orElse(0);
        double pad = (maxPrice - minPrice) * 0.08; if (pad == 0) pad = 1;
        maxPrice += pad; minPrice -= pad;

        double candleW = 700.0 / count;
        int halfW = (int) (candleW * 0.35); if (halfW < 1) halfW = 1;

        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        g.setColor(Color.DARK_GRAY);
        g.drawString(title, 20, 25);

        for (int i = 0; i < count; i++) {
            var p = points.get(i);
            double cx = 70 + i * candleW + candleW / 2;
            boolean up = p.getClose().compareTo(p.getOpen()) >= 0;
            g.setColor(up ? new Color(0xE53935) : new Color(0x4CAF50));

            double oy = 50 + 400 * (maxPrice - p.getOpen().doubleValue()) / (maxPrice - minPrice);
            double cy = 50 + 400 * (maxPrice - p.getClose().doubleValue()) / (maxPrice - minPrice);
            double hy = 50 + 400 * (maxPrice - p.getHigh().doubleValue()) / (maxPrice - minPrice);
            double ly = 50 + 400 * (maxPrice - p.getLow().doubleValue()) / (maxPrice - minPrice);

            g.draw(new Line2D.Double(cx, hy, cx, ly));
            double top = Math.min(oy, cy);
            double bot = Math.max(oy, cy);
            int barH = Math.max((int) (bot - top), 1);
            g.fill(new Rectangle2D.Double(cx - halfW, top, halfW * 2, barH));
        }

        g.dispose();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("生成K线图失败", e);
            return null;
        }
    }

    public byte[] generateKLineChart(List<KLineService.KLineData> data, String title) {
        if (data == null || data.isEmpty()) return null;
        byte[] bytes = generateKLineChartFromPoints(title, convertData(data));
        if (bytes != null) return bytes;
        return null;
    }

    private List<CandlestickPoint> convertData(List<KLineService.KLineData> data) {
        return data.stream().map(d -> new CandlestickPoint(
            d.getDay(),
            BigDecimal.valueOf(d.getOpen()),
            BigDecimal.valueOf(d.getHigh()),
            BigDecimal.valueOf(d.getLow()),
            BigDecimal.valueOf(d.getClose()),
            (long) d.getVolume(),
            ""
        )).toList();
    }
}
