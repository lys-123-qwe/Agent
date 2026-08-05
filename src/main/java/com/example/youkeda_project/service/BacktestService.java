package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 量化回测引擎 —— 基于历史K线数据模拟交易策略。
 *
 * <p>支持策略: ma_cross, rsi, macd, bollinger, grid, buy_hold</p>
 * <p>资产类型: A股 / 港股 / 美股 / 国内期货</p>
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    /** A股单笔交易费用: 佣金约 0.025%（免印花税时）+ 印花税 0.05%（卖出时） */
    private static final double A_SHARE_COMMISSION = 0.00025;
    private static final double A_SHARE_STAMP_TAX = 0.0005;  // 卖出时
    private static final int A_SHARE_LOT = 100;

    /** 港股费用 */
    private static final double HK_COMMISSION = 0.0003;
    private static final double HK_STAMP_TAX = 0.001;  // 买卖双向

    /** 美股费用 */
    private static final double US_COMMISSION = 0.00005;  // 极低

    private final StockService stockService;
    private final FuturesService futuresService;

    public BacktestService(StockService stockService, FuturesService futuresService) {
        this.stockService = stockService;
        this.futuresService = futuresService;
    }

    // ==================== 主入口 ====================

    /**
     * 执行回测。
     *
     * @param asset   股票/期货名称或代码, 如 "贵州茅台" / "gb_aapl"
     * @param strategy 策略名: ma_cross, rsi, macd, bollinger, grid, buy_hold
     * @param capital  初始资金（元）
     * @param days     回测天数（自然日）
     * @return 回测结果
     */
    public BacktestResult backtest(String asset, String strategy, double capital, int days) {
        // 1. 拉历史K线（优先股票，失败尝试期货）
        List<Bar> bars = fetchBars(asset, days);
        if (bars.isEmpty()) {
            return BacktestResult.error("未获取到 " + asset + " 的历史K线数据");
        }
        if (bars.size() < 20) {
            return BacktestResult.error(asset + " 历史数据不足（仅 " + bars.size() + " 天），需要至少 20 天");
        }

        // 2. 计算技术指标
        calculateMA(bars, 5);
        calculateMA(bars, 10);
        calculateMA(bars, 20);
        calculateMA(bars, 60);
        calculateRSI(bars, 14);
        calculateMACD(bars);
        calculateBollinger(bars, 20);

        // 3. 判断资产类型
        AssetType type = detectAssetType(asset);

        // 4. 执行策略
        List<Trade> trades = switch (strategy.toLowerCase()) {
            case "ma_cross", "ma" -> simulateMACross(bars, capital, type);
            case "rsi" -> simulateRSI(bars, capital, type);
            case "macd" -> simulateMACD(bars, capital, type);
            case "bollinger", "boll" -> simulateBollinger(bars, capital, type);
            case "grid" -> simulateGrid(bars, capital, type);
            case "buy_hold", "buyhold", "hold" -> simulateBuyHold(bars, capital, type);
            default -> simulateBuyHold(bars, capital, type);  // 默认基准
        };

        // 5. 计算基准（买入持有）
        List<Trade> benchmark = simulateBuyHold(bars, capital, type);

        // 6. 汇总指标
        return buildResult(bars, trades, benchmark, strategy, capital, type);
    }

    // ==================== 策略实现 ====================

    /** MA 金叉/死叉策略 */
    private List<Trade> simulateMACross(List<Bar> bars, double capital, AssetType type) {
        List<Trade> trades = new ArrayList<>();
        double cash = capital;
        int shares = 0;
        int fast = 5, slow = 20;

        for (int i = slow; i < bars.size(); i++) {
            Bar prev = bars.get(i - 1);
            Bar curr = bars.get(i);
            double prevFast = prev.ma(fast), prevSlow = prev.ma(slow);
            double currFast = curr.ma(fast), currSlow = curr.ma(slow);
            double price = curr.close;

            // 金叉买入
            if (prevFast <= prevSlow && currFast > currSlow && cash > 0) {
                int buyQty = maxShares(cash, price, type);
                if (buyQty > 0) {
                    double cost = buyQty * price + fee(buyQty * price, type, false);
                    cash -= cost;
                    shares = buyQty;
                    trades.add(new Trade(curr.date, "BUY", price, buyQty, cost));
                }
            }
            // 死叉卖出
            else if (prevFast >= prevSlow && currFast < currSlow && shares > 0) {
                double revenue = shares * price - fee(shares * price, type, true);
                cash += revenue;
                double pnl = revenue - trades.stream().filter(t -> t.action.equals("BUY"))
                        .mapToDouble(t -> t.amount).sum();
                trades.add(new Trade(curr.date, "SELL", price, shares, revenue));
                shares = 0;
            }
        }
        // 强制平仓
        if (shares > 0) {
            Bar last = bars.get(bars.size() - 1);
            double revenue = shares * last.close - fee(shares * last.close, type, true);
            cash += revenue;
            trades.add(new Trade(last.date, "SELL", last.close, shares, revenue));
        }
        return trades;
    }

    /** RSI 超卖/超买策略 */
    private List<Trade> simulateRSI(List<Bar> bars, double capital, AssetType type) {
        List<Trade> trades = new ArrayList<>();
        double cash = capital;
        int shares = 0;

        for (int i = 14; i < bars.size(); i++) {
            Bar curr = bars.get(i);
            double rsi = curr.rsi;

            if (rsi < 30 && cash > 0 && shares == 0) {
                int buyQty = maxShares(cash, curr.close, type);
                if (buyQty > 0) {
                    double cost = buyQty * curr.close + fee(buyQty * curr.close, type, false);
                    cash -= cost;
                    shares = buyQty;
                    trades.add(new Trade(curr.date, "BUY", curr.close, buyQty, cost));
                }
            } else if (rsi > 70 && shares > 0) {
                double revenue = shares * curr.close - fee(shares * curr.close, type, true);
                cash += revenue;
                trades.add(new Trade(curr.date, "SELL", curr.close, shares, revenue));
                shares = 0;
            }
        }
        if (shares > 0) {
            Bar last = bars.get(bars.size() - 1);
            double revenue = shares * last.close - fee(shares * last.close, type, true);
            cash += revenue;
            trades.add(new Trade(last.date, "SELL", last.close, shares, revenue));
        }
        return trades;
    }

    /** MACD 金叉/死叉策略 */
    private List<Trade> simulateMACD(List<Bar> bars, double capital, AssetType type) {
        List<Trade> trades = new ArrayList<>();
        double cash = capital;
        int shares = 0;

        for (int i = 26; i < bars.size(); i++) {
            Bar prev = bars.get(i - 1);
            Bar curr = bars.get(i);

            if (prev.macd <= prev.macdSignal && curr.macd > curr.macdSignal && cash > 0 && shares == 0) {
                int buyQty = maxShares(cash, curr.close, type);
                if (buyQty > 0) {
                    double cost = buyQty * curr.close + fee(buyQty * curr.close, type, false);
                    cash -= cost;
                    shares = buyQty;
                    trades.add(new Trade(curr.date, "BUY", curr.close, buyQty, cost));
                }
            } else if (prev.macd >= prev.macdSignal && curr.macd < curr.macdSignal && shares > 0) {
                double revenue = shares * curr.close - fee(shares * curr.close, type, true);
                cash += revenue;
                trades.add(new Trade(curr.date, "SELL", curr.close, shares, revenue));
                shares = 0;
            }
        }
        if (shares > 0) {
            Bar last = bars.get(bars.size() - 1);
            double revenue = shares * last.close - fee(shares * last.close, type, true);
            cash += revenue;
            trades.add(new Trade(last.date, "SELL", last.close, shares, revenue));
        }
        return trades;
    }

    /** 布林带策略（触及下轨买，触及上轨卖） */
    private List<Trade> simulateBollinger(List<Bar> bars, double capital, AssetType type) {
        List<Trade> trades = new ArrayList<>();
        double cash = capital;
        int shares = 0;

        for (int i = 20; i < bars.size(); i++) {
            Bar curr = bars.get(i);
            double price = curr.close;

            if (price <= curr.bollLower && cash > 0 && shares == 0) {
                int buyQty = maxShares(cash, price, type);
                if (buyQty > 0) {
                    double cost = buyQty * price + fee(buyQty * price, type, false);
                    cash -= cost;
                    shares = buyQty;
                    trades.add(new Trade(curr.date, "BUY", price, buyQty, cost));
                }
            } else if (price >= curr.bollUpper && shares > 0) {
                double revenue = shares * price - fee(shares * price, type, true);
                cash += revenue;
                trades.add(new Trade(curr.date, "SELL", price, shares, revenue));
                shares = 0;
            }
        }
        if (shares > 0) {
            Bar last = bars.get(bars.size() - 1);
            double revenue = shares * last.close - fee(shares * last.close, type, true);
            cash += revenue;
            trades.add(new Trade(last.date, "SELL", last.close, shares, revenue));
        }
        return trades;
    }

    /** 网格交易策略（固定档位高抛低吸） */
    private List<Trade> simulateGrid(List<Bar> bars, double capital, AssetType type) {
        List<Trade> trades = new ArrayList<>();
        // 以中轨为中心，上下各 3 档，每档 5%
        double base = bars.get(0).close;
        double halfCash = capital / 2;
        double cash = capital;
        int shares = maxShares(halfCash, base, type);
        double cost = shares * base + fee(shares * base, type, false);
        cash -= cost;
        trades.add(new Trade(bars.get(0).date, "BUY", base, shares, cost));
        double lastSellPrice = base;

        for (int i = 1; i < bars.size(); i++) {
            Bar curr = bars.get(i);
            double price = curr.close;

            // 涨 5% 卖
            if (price >= lastSellPrice * 1.05 && shares > 0) {
                int sellQty = shares / 3;  // 每次卖 1/3
                if (sellQty == 0) sellQty = shares;
                double revenue = sellQty * price - fee(sellQty * price, type, true);
                cash += revenue;
                shares -= sellQty;
                lastSellPrice = price;
                trades.add(new Trade(curr.date, "SELL", price, sellQty, revenue));
            }
            // 跌 5% 买
            if (price <= lastSellPrice * 0.95 && cash > 1000) {
                int buyQty = maxShares(cash * 0.3, price, type);
                if (buyQty > 0) {
                    double c = buyQty * price + fee(buyQty * price, type, false);
                    cash -= c;
                    shares += buyQty;
                    lastSellPrice = price;
                    trades.add(new Trade(curr.date, "BUY", price, buyQty, c));
                }
            }
        }
        if (shares > 0) {
            Bar last = bars.get(bars.size() - 1);
            double revenue = shares * last.close - fee(shares * last.close, type, true);
            cash += revenue;
            trades.add(new Trade(last.date, "SELL", last.close, shares, revenue));
        }
        return trades;
    }

    /** 买入持有（基准策略） */
    private List<Trade> simulateBuyHold(List<Bar> bars, double capital, AssetType type) {
        List<Trade> trades = new ArrayList<>();
        int shares = maxShares(capital, bars.get(0).close, type);
        if (shares > 0) {
            double cost = shares * bars.get(0).close + fee(shares * bars.get(0).close, type, false);
            trades.add(new Trade(bars.get(0).date, "BUY", bars.get(0).close, shares, cost));
        }
        Bar last = bars.get(bars.size() - 1);
        if (shares > 0) {
            double revenue = shares * last.close - fee(shares * last.close, type, true);
            trades.add(new Trade(last.date, "SELL", last.close, shares, revenue));
        }
        return trades;
    }

    // ==================== 技术指标计算 ====================

    private void calculateMA(List<Bar> bars, int period) {
        for (int i = period - 1; i < bars.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += bars.get(j).close;
            bars.get(i).setMA(period, sum / period);
        }
    }

    private void calculateRSI(List<Bar> bars, int period) {
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period && i < bars.size(); i++) {
            double change = bars.get(i).close - bars.get(i - 1).close;
            if (change > 0) avgGain += change;
            else avgLoss += -change;
        }
        avgGain /= period;
        avgLoss /= period;
        if (avgLoss == 0) bars.get(Math.min(period, bars.size() - 1)).rsi = 100;
        else bars.get(Math.min(period, bars.size() - 1)).rsi = 100 - 100 / (1 + avgGain / avgLoss);

        for (int i = period + 1; i < bars.size(); i++) {
            double change = bars.get(i).close - bars.get(i - 1).close;
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? -change : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            bars.get(i).rsi = avgLoss == 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
        }
    }

    private void calculateMACD(List<Bar> bars) {
        if (bars.size() < 26) return;
        // EMA12 和 EMA26
        double ema12 = bars.get(0).close;
        double ema26 = bars.get(0).close;
        for (int i = 1; i < bars.size(); i++) {
            ema12 = ema12 * (11.0 / 13) + bars.get(i).close * (2.0 / 13);
            ema26 = ema26 * (25.0 / 27) + bars.get(i).close * (2.0 / 27);
            if (i >= 25) {
                bars.get(i).macd = ema12 - ema26;
            }
        }
        // DEA (Signal) = EMA9 of DIF
        double dea = 0;
        int first = 25;
        for (int i = first; i < bars.size(); i++) {
            dea = dea * (8.0 / 10) + bars.get(i).macd * (2.0 / 10);
            bars.get(i).macdSignal = dea;
        }
    }

    private void calculateBollinger(List<Bar> bars, int period) {
        for (int i = period - 1; i < bars.size(); i++) {
            double ma = bars.get(i).ma(period);
            double sumSq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sumSq += Math.pow(bars.get(j).close - ma, 2);
            }
            double std = Math.sqrt(sumSq / period);
            bars.get(i).bollUpper = ma + 2 * std;
            bars.get(i).bollLower = ma - 2 * std;
        }
    }

    // ==================== 指标汇总 ====================

    private BacktestResult buildResult(List<Bar> bars, List<Trade> trades,
                                        List<Trade> benchmark, String strategy,
                                        double capital, AssetType type) {
        // 总盈亏
        double buySum = trades.stream().filter(t -> t.action.equals("BUY")).mapToDouble(t -> t.amount).sum();
        double sellSum = trades.stream().filter(t -> t.action.equals("SELL")).mapToDouble(t -> t.amount).sum();
        int totalBought = trades.stream().filter(t -> t.action.equals("BUY")).mapToInt(t -> t.shares).sum();
        int totalSold = trades.stream().filter(t -> t.action.equals("SELL")).mapToInt(t -> t.shares).sum();
        int remainingShares = totalBought - totalSold;
        double remainValue = remainingShares > 0 ? remainingShares * bars.get(bars.size() - 1).close : 0;
        double finalValue = capital - buySum + sellSum + remainValue;
        double totalReturn = (finalValue - capital) / capital * 100;
        double totalPnl = finalValue - capital;

        // 基准
        double benchBuy = benchmark.stream().filter(t -> t.action.equals("BUY")).mapToDouble(t -> t.amount).sum();
        double benchSell = benchmark.stream().filter(t -> t.action.equals("SELL")).mapToDouble(t -> t.amount).sum();
        double benchReturn = (capital - benchBuy + benchSell - capital) / capital * 100;

        // 胜率: 用配对法 — 每笔买入后紧跟的卖出算一次完整交易
        double lastBuyPrice = 0;
        int wins = 0, completed = 0;
        for (Trade t : trades) {
            if (t.action.equals("BUY")) {
                lastBuyPrice = t.price;
            } else if (t.action.equals("SELL") && lastBuyPrice > 0) {
                if (t.price > lastBuyPrice) wins++;
                completed++;
                lastBuyPrice = 0;
            }
        }
        double winRate = completed > 0 ? (double) wins / completed * 100 : 0;

        // 最大回撤: 逐日模拟净值
        double peak = capital;
        double maxDD = 0;
        int pos = 0;
        double cash = capital;
        int tIdx = 0;
        // 先扣除所有买入资金
        for (Trade t : trades) {
            if (t.action.equals("BUY")) { cash -= t.amount; pos += t.shares; }
            else { cash += t.amount; pos -= t.shares; }
        }
        // 简化：用最终持仓和现金算每日净值
        for (int i = 0; i < bars.size(); i++) {
            double nav = cash + pos * bars.get(i).close;
            if (nav > peak) peak = nav;
            double dd = peak > 0 ? (peak - nav) / peak * 100 : 0;
            if (dd > maxDD) maxDD = dd;
        }

        // 年化
        double years = (double) bars.size() / 252;
        double annualizedReturn = years > 0 ? (Math.pow(1 + totalReturn / 100, 1.0 / years) - 1) * 100 : totalReturn;

        int buyCount = (int) trades.stream().filter(t -> t.action.equals("BUY")).count();

        return new BacktestResult(
                "SUCCESS", strategy, capital, finalValue, totalPnl, totalReturn,
                annualizedReturn, maxDD, winRate, buyCount, completed,
                benchReturn,
                bars.get(0).date, bars.get(bars.size() - 1).date, bars.size(),
                trades, null
        );
    }

    // ==================== 辅助方法 ====================

    /** 拉历史K线 */
    private List<Bar> fetchBars(String asset, int days) {
        // 先尝试股票
        List<StockService.KlineData> stockKL = stockService.getHistoricalKline(asset, days);
        if (!stockKL.isEmpty()) {
            return stockKL.stream().map(k -> new Bar(k.date(),
                    parseDouble(k.open()), parseDouble(k.high()),
                    parseDouble(k.low()), parseDouble(k.close()),
                    parseDouble(k.volume()))).toList();
        }
        // 尝试期货
        List<FuturesService.KlineData> futuresKL = futuresService.getHistoricalKline(asset, days);
        if (!futuresKL.isEmpty()) {
            return futuresKL.stream().map(k -> new Bar(k.date(),
                    parseDouble(k.open()), parseDouble(k.high()),
                    parseDouble(k.low()), parseDouble(k.close()),
                    parseDouble(k.volume()))).toList();
        }
        return List.of();
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (Exception e) { return 0; }
    }

    /** 买入时计算最大可买股数 */
    private int maxShares(double cash, double price, AssetType type) {
        if (price <= 0) return 0;
        int lot = (type == AssetType.A_SHARE || type == AssetType.HK) ? A_SHARE_LOT : 1;
        int qty = (int) (cash / price);
        return (qty / lot) * lot;
    }

    /** 交易费用 */
    private double fee(double amount, AssetType type, boolean isSell) {
        return switch (type) {
            case A_SHARE -> amount * A_SHARE_COMMISSION + (isSell ? amount * A_SHARE_STAMP_TAX : 0);
            case HK -> amount * (HK_COMMISSION + HK_STAMP_TAX);
            case US -> amount * US_COMMISSION;
            case FUTURES -> amount * 0.0003;  // 期货手续费约万三
        };
    }

    private AssetType detectAssetType(String asset) {
        String code = asset.trim();
        if (code.startsWith("gb_")) return AssetType.US;
        if (code.startsWith("hk")) return AssetType.HK;
        if (code.startsWith("nf_") || code.startsWith("hf_")) return AssetType.FUTURES;
        return AssetType.A_SHARE;
    }

    // ==================== 数据结构 ====================

    /** 单日K线条 */
    static class Bar {
        final String date;
        final double open, high, low, close;
        final double volume;
        double rsi;
        double macd, macdSignal;
        double bollUpper, bollLower;
        final double[] mas = new double[5];  // index by period

        Bar(String date, double open, double high, double low, double close, double volume) {
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }

        double ma(int period) {
            return switch (period) { case 5 -> mas[0]; case 10 -> mas[1]; case 20 -> mas[2]; case 60 -> mas[3]; default -> 0; };
        }

        void setMA(int period, double value) {
            switch (period) { case 5 -> mas[0] = value; case 10 -> mas[1] = value; case 20 -> mas[2] = value; case 60 -> mas[3] = value; }
        }
    }

    /** 单笔交易记录 */
    public record Trade(String date, String action, double price, int shares, double amount) {}

    /** 资产类型 */
    enum AssetType { A_SHARE, HK, US, FUTURES }

    /** 回测结果 */
    public record BacktestResult(
            String status,
            String strategy,
            double initialCapital,
            double finalValue,
            double totalPnl,
            double totalReturnPct,
            double annualizedReturnPct,
            double maxDrawdownPct,
            double winRatePct,
            int totalTrades,
            int completedTrades,
            double benchmarkReturnPct,
            String startDate,
            String endDate,
            int dataDays,
            List<Trade> trades,
            String error
    ) {
        static BacktestResult error(String msg) {
            return new BacktestResult("ERROR", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", "", 0, List.of(), msg);
        }

        /** 格式化为 AI 可读文本 */
        public String toText() {
            if ("ERROR".equals(status)) return "回测失败: " + error;

            StringBuilder sb = new StringBuilder();
            sb.append("📊 量化回测报告\n\n");
            sb.append("| 指标 | 数值 |\n");
            sb.append("|------|------|\n");
            sb.append(String.format("| **策略** | %s |\n", strategyName()));
            sb.append(String.format("| **初始资金** | ¥%,.0f |\n", initialCapital));
            sb.append(String.format("| **最终市值** | ¥%,.0f |\n", finalValue));
            sb.append(String.format("| **总盈亏** | %s¥%,.0f |\n", totalPnl >= 0 ? "+" : "", totalPnl));
            sb.append(String.format("| **总收益率** | %s%.2f%% |\n", totalReturnPct >= 0 ? "+" : "", totalReturnPct));
            sb.append(String.format("| **年化收益率** | %s%.2f%% |\n", annualizedReturnPct >= 0 ? "+" : "", annualizedReturnPct));
            sb.append(String.format("| **最大回撤** | %.2f%% |\n", maxDrawdownPct));
            sb.append(String.format("| **胜率** | %.1f%% (%d/%d) |\n", winRatePct, completedTrades > 0 ? (int)(winRatePct * completedTrades / 100) : 0, completedTrades));
            sb.append(String.format("| **交易次数** | %d 笔 |\n", totalTrades));
            sb.append(String.format("| **基准(买入持有)** | %s%.2f%% |\n", benchmarkReturnPct >= 0 ? "+" : "", benchmarkReturnPct));
            sb.append(String.format("| **数据区间** | %s ~ %s (%d个交易日) |\n", startDate, endDate, dataDays));

            // 超额收益
            double excess = totalReturnPct - benchmarkReturnPct;
            sb.append(String.format("| **超额收益** | %s%.2f%% |\n", excess >= 0 ? "+" : "", excess));

            sb.append("\n> 📌 以上回测基于历史数据模拟，不构成投资建议。过往业绩不代表未来表现。");

            return sb.toString();
        }

        private String strategyName() {
            return switch (strategy) {
                case "ma_cross" -> "MA金叉死叉";
                case "rsi" -> "RSI超买超卖";
                case "macd" -> "MACD策略";
                case "bollinger" -> "布林带策略";
                case "grid" -> "网格交易";
                case "buy_hold" -> "买入持有（基准）";
                default -> strategy;
            };
        }
    }
}
