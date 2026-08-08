package com.example.youkeda_project.service.stock;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
/** 技术指标计算（RSI 等）。 */


@Service
public class TechnicalService {

    public static class MaResult {
        private final int period;
        private final double value;

        public MaResult(int period, double value) {
            this.period = period;
            this.value = value;
        }

        public int getPeriod() { return period; }
        public double getValue() { return value; }
    }

    public static class RsiResult {
        private final int period;
        private final double value;

        public RsiResult(int period, double value) {
            this.period = period;
            this.value = value;
        }

        public int getPeriod() { return period; }
        public double getValue() { return value; }
    }

    public List<MaResult> calcMA(List<KLineService.KLineData> data, List<Integer> periods) {
        List<MaResult> results = new ArrayList<>();
        if (data == null || data.isEmpty()) return results;

        for (int period : periods) {
            if (data.size() < period) continue;
            double sum = 0;
            for (int i = data.size() - period; i < data.size(); i++) {
                sum += data.get(i).getClose();
            }
            results.add(new MaResult(period, roundTo2(sum / period)));
        }
        return results;
    }

    public MaResult calcMA(List<KLineService.KLineData> data, int period) {
        List<MaResult> list = calcMA(data, List.of(period));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<RsiResult> calcRSI(List<KLineService.KLineData> data, List<Integer> periods) {
        List<RsiResult> results = new ArrayList<>();
        if (data == null || data.size() < 2) return results;

        // 计算每日涨跌幅
        double[] changes = new double[data.size()];
        for (int i = 1; i < data.size(); i++) {
            changes[i] = data.get(i).getClose() - data.get(i - 1).getClose();
        }

        for (int period : periods) {
            if (data.size() < period + 1) continue;
            double rsi = calcRsiFromChanges(changes, period);
            results.add(new RsiResult(period, roundTo2(rsi)));
        }
        return results;
    }

    public RsiResult calcRSI(List<KLineService.KLineData> data, int period) {
        List<RsiResult> list = calcRSI(data, List.of(period));
        return list.isEmpty() ? null : list.get(0);
    }

    private double calcRsiFromChanges(double[] changes, int period) {
        int start = changes.length - period;
        double avgGain = 0, avgLoss = 0;

        for (int i = start; i < changes.length; i++) {
            if (changes[i] > 0) avgGain += changes[i];
            else avgLoss -= changes[i];
        }
        avgGain /= period;
        avgLoss /= period;

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    public String formatAnalysis(List<KLineService.KLineData> data,
                                  List<Integer> maPeriods, List<Integer> rsiPeriods) {
        if (data == null || data.size() < 2) return "暂无数据";

        var latest = data.get(data.size() - 1);
        double change = latest.getClose() - data.get(data.size() - 2).getClose();
        double changePct = data.get(data.size() - 2).getClose() > 0
            ? change / data.get(data.size() - 2).getClose() * 100 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%.2f  涨跌:%+.2f(%+.2f%%)  开:%.2f 高:%.2f 低:%.2f\n",
            latest.getClose(), change, changePct, latest.getOpen(),
            latest.getHigh(), latest.getLow()));

        // MA
        if (maPeriods != null && !maPeriods.isEmpty()) {
            var mas = calcMA(data, maPeriods);
            sb.append("MA: ");
            for (var ma : mas) {
                double diff = latest.getClose() - ma.getValue();
                sb.append(String.format("MA%d=%.2f(%+.2f)  ", ma.getPeriod(), ma.getValue(), diff));
            }
            sb.append("\n");
        }

        // RSI
        if (rsiPeriods != null && !rsiPeriods.isEmpty()) {
            var rsis = calcRSI(data, rsiPeriods);
            sb.append("RSI: ");
            for (var rsi : rsis) {
                String status = rsi.getValue() > 70 ? "⚠超买" : rsi.getValue() < 30 ? "⚠超卖" : "";
                sb.append(String.format("RSI%d=%.1f%s  ", rsi.getPeriod(), rsi.getValue(), status));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private double roundTo2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
