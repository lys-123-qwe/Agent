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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ChartService {

    private static final Logger log = LoggerFactory.getLogger(ChartService.class);

    private static final int W = 1600;
    private static final int H = 1000;
    private static final int MARGIN_L = 60;
    private static final int MARGIN_R = 20;
    private static final int MARGIN_T = 40;
    private static final int MARGIN_B = 50;
    private static final int CHART_W = W - MARGIN_L - MARGIN_R;
    private static final int CHART_H = H - MARGIN_T - MARGIN_B;

    private static final Color UP_COLOR = new Color(0xE53935);
    private static final Color DOWN_COLOR = new Color(0x4CAF50);
    private static final Color MA5_COLOR = new Color(0x2196F3);
    private static final Color MA10_COLOR = new Color(0xFF9800);
    private static final Color MA20_COLOR = new Color(0x9C27B0);
    private static final Color GRID_COLOR = new Color(0xE0E0E0);

    public byte[] generateKLineChart(List<KLineService.KLineData> data, String title) {
        if (data == null || data.isEmpty()) return null;

        int count = Math.min(data.size(), 60);
        data = data.subList(data.size() - count, data.size());

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        double maxPrice = data.stream().mapToDouble(d -> d.getHigh()).max().orElse(100);
        double minPrice = data.stream().mapToDouble(d -> d.getLow()).min().orElse(0);
        double pad = (maxPrice - minPrice) * 0.08;
        if (pad == 0) pad = 1;
        maxPrice += pad;
        minPrice -= pad;

        double maxVol = data.stream().mapToDouble(d -> d.getVolume()).max().orElse(1);
        double candleW = (double) CHART_W / count;
        int halfW = (int) (candleW * 0.35);
        if (halfW < 1) halfW = 1;

        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();

        g.setColor(GRID_COLOR);
        for (int i = 1; i <= 4; i++) {
            int y = MARGIN_T + CHART_H * i / 5;
            g.drawLine(MARGIN_L, y, W - MARGIN_R, y);
            String label = String.format("%.2f", maxPrice - (maxPrice - minPrice) * i / 5);
            g.setColor(Color.DARK_GRAY);
            g.drawString(label, 2, y + fm.getHeight() / 3);
            g.setColor(GRID_COLOR);
        }

        for (int i = 0; i < count; i++) {
            var d = data.get(i);
            double cx = MARGIN_L + i * candleW + candleW / 2;
            boolean up = d.getClose() >= d.getOpen();
            g.setColor(up ? UP_COLOR : DOWN_COLOR);

            double oy = priceToY(d.getOpen(), minPrice, maxPrice);
            double cy = priceToY(d.getClose(), minPrice, maxPrice);
            double hy = priceToY(d.getHigh(), minPrice, maxPrice);
            double ly = priceToY(d.getLow(), minPrice, maxPrice);

            g.draw(new Line2D.Double(cx, hy, cx, ly));
            double top = Math.min(oy, cy);
            double bot = Math.max(oy, cy);
            int barH = Math.max((int) (bot - top), 1);
            g.fill(new Rectangle2D.Double(cx - halfW, top, halfW * 2, barH));

            double volRatio = d.getVolume() / maxVol;
            int volH = (int) (volRatio * 80);
            g.setColor(up ? UP_COLOR.darker() : DOWN_COLOR.darker());
            g.fill(new Rectangle2D.Double(cx - halfW / 2, H - MARGIN_B - volH, halfW, volH));
        }

        g.setColor(MA5_COLOR);
        drawMALine(g, data, minPrice, maxPrice, 5, count, candleW);
        g.setColor(MA10_COLOR);
        drawMALine(g, data, minPrice, maxPrice, 10, count, candleW);
        g.setColor(MA20_COLOR);
        drawMALine(g, data, minPrice, maxPrice, 20, count, candleW);

        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        fm = g.getFontMetrics();
        g.drawString(title, (W - fm.stringWidth(title)) / 2, 25);

        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        g.setColor(MA5_COLOR);
        g.drawString("MA5", W - MARGIN_R - 60, 70);
        g.setColor(MA10_COLOR);
        g.drawString("MA10", W - MARGIN_R - 60, 85);
        g.setColor(MA20_COLOR);
        g.drawString("MA20", W - MARGIN_R - 60, 100);

        g.dispose();
        return toBytes(img);
    }

    private byte[] toBytes(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("生成K线图失败: {}", e.getMessage());
            return null;
        }
    }

    private double priceToY(double price, double min, double max) {
        return MARGIN_T + CHART_H * (max - price) / (max - min);
    }

    private void drawMALine(Graphics2D g, List<KLineService.KLineData> data,
                            double minPrice, double maxPrice, int period,
                            int count, double candleW) {
        for (int i = period; i < count; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += data.get(j).getClose();
            double ma = sum / period;
            double x = MARGIN_L + i * candleW + candleW / 2;
            double y = priceToY(ma, minPrice, maxPrice);

            if (i == period) {
                double px = MARGIN_L + (i - period) * candleW + candleW / 2;
                double py = priceToY(ma, minPrice, maxPrice);
                g.draw(new Line2D.Double(px, py, x, y));
            } else {
                double prevSum = 0;
                for (int j = i - period; j < i; j++) prevSum += data.get(j).getClose();
                double prevMa = prevSum / period;
                double px = MARGIN_L + (i - 1) * candleW + candleW / 2;
                double py = priceToY(prevMa, minPrice, maxPrice);
                g.draw(new Line2D.Double(px, py, x, y));
            }
        }
    }
}
