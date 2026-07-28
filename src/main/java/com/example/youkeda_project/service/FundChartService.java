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
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 基金涨幅折线图生成服务。
 * 纯 Java2D 实现，无第三方依赖。
 */
@Service
public class FundChartService {

    private static final Logger log = LoggerFactory.getLogger(FundChartService.class);

    // 画布尺寸
    private static final int WIDTH = 800;
    private static final int HEIGHT = 500;

    // 边距
    private static final int PAD_LEFT = 80;
    private static final int PAD_RIGHT = 40;
    private static final int PAD_TOP = 80;
    private static final int PAD_BOTTOM = 80;

    // 颜色方案（深色背景）
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

    // 字体
    private static Font fontTitle;
    private static Font fontLabel;
    private static Font fontAxis;
    private static Font fontSmall;

    static {
        try {
            Font baseFont = Font.createFont(Font.TRUETYPE_FONT,
                new java.io.File("C:\\Windows\\Fonts\\msyh.ttc"));
            fontTitle = baseFont.deriveFont(Font.BOLD, 22f);
            fontLabel = baseFont.deriveFont(Font.PLAIN, 13f);
            fontAxis = baseFont.deriveFont(Font.PLAIN, 12f);
            fontSmall = baseFont.deriveFont(Font.PLAIN, 11f);
        } catch (Exception e) {
            fontTitle = new Font("SansSerif", Font.BOLD, 22);
            fontLabel = new Font("SansSerif", Font.PLAIN, 13);
            fontAxis = new Font("SansSerif", Font.PLAIN, 12);
            fontSmall = new Font("SansSerif", Font.PLAIN, 11);
        }
    }

    /**
     * 生成基金涨幅折线图。
     *
     * @param fundName 基金名称
     * @param code     基金代码
     * @param data     历史净值数据（正序，从旧到新）
     * @return PNG 图片字节数组
     */
    public byte[] generateChart(String fundName, String code, List<FundService.NavData> data) {
        if (data == null || data.size() < 2) {
            log.warn("数据不足，无法生成图表: size={}", data == null ? 0 : data.size());
            return null;
        }

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // 开启抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 背景
        g.setColor(BG_COLOR);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 绘图区域
        int plotX = PAD_LEFT;
        int plotY = PAD_TOP;
        int plotW = WIDTH - PAD_LEFT - PAD_RIGHT;
        int plotH = HEIGHT - PAD_TOP - PAD_BOTTOM;

        // 解析净值
        BigDecimal[] navs = new BigDecimal[data.size()];
        BigDecimal minNav = new BigDecimal("999999");
        BigDecimal maxNav = new BigDecimal("0");
        for (int i = 0; i < data.size(); i++) {
            navs[i] = new BigDecimal(data.get(i).nav().trim());
            if (navs[i].compareTo(minNav) < 0) minNav = navs[i];
            if (navs[i].compareTo(maxNav) > 0) maxNav = navs[i];
        }

        // 添加上下边距
        BigDecimal range = maxNav.subtract(minNav);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            range = new BigDecimal("0.01");
        }
        BigDecimal padding = range.multiply(new BigDecimal("0.1"));
        minNav = minNav.subtract(padding);
        maxNav = maxNav.add(padding);
        range = maxNav.subtract(minNav);

        // 绘制网格线
        drawGrid(g, plotX, plotY, plotW, plotH, minNav, maxNav);

        // 绘制折线和填充
        drawLine(g, data, navs, plotX, plotY, plotW, plotH, minNav, maxNav);

        // 绘制标题
        drawTitle(g, fundName, code, data);

        // 绘制X轴日期标签
        drawDateLabels(g, data, plotX, plotY, plotW, plotH);

        // 绘制图例
        drawLegend(g);

        g.dispose();

        // 输出 PNG
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] bytes = baos.toByteArray();
            log.info("图表生成成功: {} bytes, dataPoints={}", bytes.length, data.size());
            return bytes;
        } catch (Exception e) {
            log.error("图表输出失败: {}", e.getMessage());
            return null;
        }
    }

    private void drawGrid(Graphics2D g, int x, int y, int w, int h,
                          BigDecimal minNav, BigDecimal maxNav) {
        g.setColor(GRID_COLOR);
        g.setStroke(new BasicStroke(0.5f));

        // 水平网格线（5条）
        int gridCount = 5;
        for (int i = 0; i <= gridCount; i++) {
            int py = y + (int) (h * i / (double) gridCount);
            g.drawLine(x, py, x + w, py);

            // Y轴标签（右侧显示净值）
            BigDecimal val = maxNav.subtract(maxNav.subtract(minNav)
                .multiply(new BigDecimal(String.valueOf(i)))
                .divide(new BigDecimal(String.valueOf(gridCount)), 4, RoundingMode.HALF_UP));
            g.setColor(LABEL_COLOR);
            g.setFont(fontSmall);
            String label = val.setScale(4, RoundingMode.HALF_UP).toString();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, x + w + 5, py + fm.getAscent() / 2 - 2);
            g.setColor(GRID_COLOR);
        }
    }

    private void drawLine(Graphics2D g, List<FundService.NavData> data,
                          BigDecimal[] navs, int x, int y, int w, int h,
                          BigDecimal minNav, BigDecimal maxNav) {
        int n = data.size();
        BigDecimal range = maxNav.subtract(minNav);
        if (range.compareTo(BigDecimal.ZERO) == 0) return;

        // 计算点坐标
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = x + (int) (w * i / (double) (n - 1));
            py[i] = y + h - (int) (h * navs[i].subtract(minNav).doubleValue() / range.doubleValue());
        }

        // 判断整体涨跌趋势
        boolean isUp = navs[n - 1].compareTo(navs[0]) >= 0;
        Color fillColor = isUp ? FILL_COLOR_UP : FILL_COLOR_DOWN;

        // 填充区域
        Polygon fillArea = new Polygon();
        fillArea.addPoint(px[0], y + h);
        for (int i = 0; i < n; i++) {
            fillArea.addPoint(px[i], py[i]);
        }
        fillArea.addPoint(px[n - 1], y + h);
        g.setColor(fillColor);
        g.fillPolygon(fillArea);

        // 折线
        g.setColor(LINE_COLOR);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < n - 1; i++) {
            g.drawLine(px[i], py[i], px[i + 1], py[i + 1]);
        }

        // 数据点
        for (int i = 0; i < n; i++) {
            // 判断当日涨跌
            String changeStr = data.get(i).changePct();
            boolean dayUp = false;
            if (changeStr != null && !changeStr.isBlank()) {
                try {
                    dayUp = new BigDecimal(changeStr.trim()).compareTo(BigDecimal.ZERO) >= 0;
                } catch (NumberFormatException ignored) {}
            }
            Color dotColor = dayUp ? UP_COLOR : DOWN_COLOR;
            g.setColor(dotColor);
            g.fillOval(px[i] - 4, py[i] - 4, 8, 8);

            // 白色边框
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1f));
            g.drawOval(px[i] - 4, py[i] - 4, 8, 8);
        }

        // 首尾标注数值
        g.setColor(TEXT_COLOR);
        g.setFont(fontSmall);
        FontMetrics fm = g.getFontMetrics();

        String firstNav = navs[0].setScale(4, RoundingMode.HALF_UP).toString();
        g.drawString(firstNav, px[0] - fm.stringWidth(firstNav) - 8, py[0] + 4);

        String lastNav = navs[n - 1].setScale(4, RoundingMode.HALF_UP).toString();
        g.drawString(lastNav, px[n - 1] + 10, py[n - 1] + 4);
    }

    private void drawTitle(Graphics2D g, String fundName, String code,
                           List<FundService.NavData> data) {
        // 标题
        g.setColor(TITLE_COLOR);
        g.setFont(fontTitle);
        String title = fundName + "（" + code + "）";
        g.drawString(title, PAD_LEFT, PAD_TOP - 35);

        // 副标题：时间范围 + 涨跌幅
        g.setFont(fontLabel);
        String startDate = data.get(0).date();
        String endDate = data.get(data.size() - 1).date();
        BigDecimal firstNav = new BigDecimal(data.get(0).nav().trim());
        BigDecimal lastNav = new BigDecimal(data.get(data.size() - 1).nav().trim());
        BigDecimal totalChange = lastNav.subtract(firstNav)
            .divide(firstNav, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        String sign = totalChange.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        Color changeColor = totalChange.compareTo(BigDecimal.ZERO) >= 0 ? UP_COLOR : DOWN_COLOR;

        String subtitle = startDate + " ~ " + endDate + "  |  区间涨幅: "
            + sign + totalChange.setScale(2, RoundingMode.HALF_UP) + "%";
        g.setColor(changeColor);
        g.drawString(subtitle, PAD_LEFT, PAD_TOP - 12);
    }

    private void drawDateLabels(Graphics2D g, List<FundService.NavData> data,
                                int x, int y, int w, int h) {
        g.setColor(LABEL_COLOR);
        g.setFont(fontSmall);
        FontMetrics fm = g.getFontMetrics();
        int n = data.size();

        // 显示最多 6 个日期标签
        int maxLabels = Math.min(6, n);
        for (int i = 0; i < maxLabels; i++) {
            int idx = (int) (i * (n - 1) / (double) (maxLabels - 1));
            int px = x + (int) (w * idx / (double) (n - 1));
            String date = data.get(idx).date();
            // 只显示 MM-dd 部分
            if (date.length() >= 10) {
                date = date.substring(5, 10);
            }
            int labelW = fm.stringWidth(date);
            g.drawString(date, px - labelW / 2, y + h + 20);
        }
    }

    private void drawLegend(Graphics2D g) {
        int x = WIDTH - PAD_RIGHT - 160;
        int y = PAD_TOP - 35;

        g.setFont(fontSmall);
        FontMetrics fm = g.getFontMetrics();

        // 红涨
        g.setColor(UP_COLOR);
        g.fillOval(x, y, 8, 8);
        g.drawString("涨", x + 12, y + 8);

        // 绿跌
        g.setColor(DOWN_COLOR);
        g.fillOval(x + 40, y, 8, 8);
        g.drawString("跌", x + 52, y + 8);
    }
}
