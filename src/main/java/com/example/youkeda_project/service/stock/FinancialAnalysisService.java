package com.example.youkeda_project.service.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 金融分析服务:基于真实 K 线数据计算 MA/RSI/MACD/布林带/ATR,
 * 输出方向判断,置信度,预测有效期,支撑/阻力,预计底部/顶部区域.
 */

@Service
public class FinancialAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FinancialAnalysisService.class);

    private final KLineService kLineService;
    private final TechnicalService technicalService;

    public FinancialAnalysisService(KLineService kLineService, TechnicalService technicalService) {
        this.kLineService = kLineService;
        this.technicalService = technicalService;
    }

    /** 分析结果对象 */
    public record AnalysisResult(
        String direction,      // 看涨 / 看空 / 震荡
        double confidence,   // 0.0 ~ 1.0
        String period,       // 预测有效期,如 "3~6小时"
        double support,      // 支撑位
        double resistance,   // 阻力位
        double expectedBottom, // 预计触底区域
        double expectedTop,    // 预计触顶区域
        String summary       // 综合分析摘要
    ) {}

    /**
     * 分析指定股票/指数的走势.
     *
     * @param name 股票/指数名称或代码
     * @param currentPrice 当前价格(可从 StockService 查询)
     * @return 分析结果
     */
    public AnalysisResult analyze(String name, double currentPrice) {
        log.info("金融分析: name={}, currentPrice={}", name, currentPrice);

        List<KLineService.KLineData> kline = kLineService.fetchDailyKLine(name, 60);
        if (kline == null || kline.size() < 20) {
            return new AnalysisResult("未知", 0, "N/A", 0, 0, 0, 0,
                "历史数据不足，无法分析");
        }
        List<Double> prices = kline.stream().map(KLineService.KLineData::getClose).toList();

        double ma5 = maValue(kline, 5);
        double ma20 = maValue(kline, 20);
        double ma60 = maValue(kline, 60);

        double rsi = rsiValue(kline, 14);
        Macd macd = macd(prices);
        Bollinger boll = bollinger(prices, 20);
        double atr = atr(prices, 14);

        // 支撑/阻力:用 MA20 和布林上下轨
        double support = Math.min(ma20, boll.lower);
        double resistance = Math.max(ma20, boll.upper);

        // 方向判断
        String direction;
        int bullishSignals = 0;
        int bearishSignals = 0;

        if (ma5 > ma20 && ma20 > ma60) bullishSignals += 2;
        if (ma5 < ma20 && ma20 < ma60) bearishSignals += 2;
        if (rsi > 50 && rsi < 70) bullishSignals += 1;
        if (rsi < 50 && rsi > 30) bearishSignals += 1;
        if (rsi > 70) bearishSignals += 1; // 超买回调风险
        if (rsi < 30) bullishSignals += 1; // 超卖反弹
        if (macd.dif > macd.dea) bullishSignals += 1;
        if (macd.dif < macd.dea) bearishSignals += 1;
        if (currentPrice > boll.upper) bearishSignals += 1;
        if (currentPrice < boll.lower) bullishSignals += 1;

        if (bullishSignals > bearishSignals + 1) {
            direction = "看涨";
        } else if (bearishSignals > bullishSignals + 1) {
            direction = "看空";
        } else {
            direction = "震荡";
        }

        // 置信度:基于信号差异和 RSI 离中值距离
        int diff = Math.abs(bullishSignals - bearishSignals);
        double confidence = Math.min(0.95, 0.45 + diff * 0.08 + Math.abs(rsi - 50) / 100.0);
        confidence = Math.round(confidence * 100) / 100.0;

        // 有效期:根据波动率 ATR
        String period;
        if (atr / currentPrice > 0.03) {
            period = "1~3小时";
        } else if (atr / currentPrice > 0.015) {
            period = "3~6小时";
        } else {
            period = "6~12小时";
        }

        // 预计底部/顶部区域:基于 ATR + 支撑/阻力
        double expectedBottom = Math.max(support - atr * 1.5, currentPrice * 0.85);
        double expectedTop = Math.min(resistance + atr * 1.5, currentPrice * 1.15);

        expectedBottom = round(expectedBottom);
        expectedTop = round(expectedTop);
        support = round(support);
        resistance = round(resistance);

        String summary = String.format(
            "[%s]技术面分析:方向=%s,置信度=%.0f%%,有效期=%s." +
            "MA5=%.2f,MA20=%.2f,MA60=%.2f;RSI(14)=%.1f;MACD DIF=%.3f,DEA=%.3f;" +
            "布林带[%.2f, %.2f, %.2f];ATR=%.2f." +
            "支撑位 %.2f,阻力位 %.2f,预计底部区域 %.2f~%.2f,预计顶部区域 %.2f~%.2f.",
            name, direction, confidence * 100, period,
            ma5, ma20, ma60, rsi, macd.dif, macd.dea,
            boll.lower, boll.mid, boll.upper, atr,
            support, resistance,
            expectedBottom, expectedBottom + atr * 0.5,
            expectedTop - atr * 0.5, expectedTop
        );

        return new AnalysisResult(direction, confidence, period, support, resistance,
            expectedBottom, expectedTop, summary);
    }

    private double maValue(List<KLineService.KLineData> kline, int period) {
        var ma = technicalService.calcMA(kline, period);
        return ma != null ? ma.getValue() : round(kline.get(kline.size() - 1).getClose());
    }

    private double rsiValue(List<KLineService.KLineData> kline, int period) {
        var rsi = technicalService.calcRSI(kline, period);
        return rsi != null ? rsi.getValue() : 50.0;
    }

    private record Macd(double dif, double dea, double hist) {}

    private Macd macd(List<Double> prices) {
        if (prices.size() < 26) {
            double last = prices.get(prices.size() - 1);
            return new Macd(round(last), round(last), 0);
        }
        // EMA12/EMA26 递推计算 DIF（与回测引擎算法一致）
        double ema12 = prices.get(0), ema26 = prices.get(0);
        double[] difs = new double[prices.size()];
        for (int i = 0; i < prices.size(); i++) {
            double p = prices.get(i);
            if (i > 0) {
                ema12 = ema12 * (11.0 / 13) + p * (2.0 / 13);
                ema26 = ema26 * (25.0 / 27) + p * (2.0 / 27);
            }
            difs[i] = ema12 - ema26;
        }
        double dif = difs[difs.length - 1];
        // DEA = DIF 的 9 周期 EMA
        int start = difs.length - Math.min(9, difs.length);
        double dea = difs[start];
        for (int i = start + 1; i < difs.length; i++) {
            dea = dea * (8.0 / 10) + difs[i] * (2.0 / 10);
        }
        double hist = dif - dea;
        return new Macd(round(dif), round(dea), round(hist));
    }

    private record Bollinger(double lower, double mid, double upper) {}

    private Bollinger bollinger(List<Double> prices, int period) {
        double mid = 0;
        int count = Math.min(period, prices.size());
        for (int i = prices.size() - count; i < prices.size(); i++) {
            mid += prices.get(i);
        }
        mid /= count;
        double variance = 0;
        for (int i = prices.size() - count; i < prices.size(); i++) {
            variance += Math.pow(prices.get(i) - mid, 2);
        }
        double std = Math.sqrt(variance / count);
        return new Bollinger(round(mid - 2 * std), round(mid), round(mid + 2 * std));
    }

    private double atr(List<Double> prices, int period) {
        if (prices.size() < period + 1) return prices.get(prices.size() - 1) * 0.01;
        double sum = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            double high = Math.max(prices.get(i), prices.get(i - 1));
            double low = Math.min(prices.get(i), prices.get(i - 1));
            sum += high - low;
        }
        return round(sum / period);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
