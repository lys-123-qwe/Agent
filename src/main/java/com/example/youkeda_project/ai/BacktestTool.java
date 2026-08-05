package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.BacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 量化回测工具（Function Calling）。
 *
 * <p>根据用户指定的股票、策略、初始资金，基于历史K线数据模拟交易，
 * 返回收益率、最大回撤、胜率等指标。</p>
 */
@Component
public class BacktestTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BacktestTool.class);

    private final BacktestService backtestService;

    public BacktestTool(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @Override
    public String getName() {
        return "backtest";
    }

    @Override
    public String getDescription() {
        return "量化回测工具——基于历史K线数据模拟交易策略，计算收益率、最大回撤、胜率等指标。"
                + "当用户问'如果买某股票会赚多少''回测一下''用XX策略能赚多少'"
                + "'如果在X时间投X钱到X股票'等问题时，必须调用此工具。"
                + "支持策略: ma_cross(金叉死叉), rsi(超买超卖), macd, bollinger(布林带), grid(网格交易), buy_hold(买入持有基准)。"
                + "支持A股/港股/美股/期货。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("type", "string");
        asset.put("description", "股票或期货名称/代码。A股: 贵州茅台/600519/sh600519; 港股: 腾讯控股/hk00700; 美股: AAPL/gb_aapl; 期货: 玉米/nf_C0。");
        properties.put("asset", asset);

        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("type", "string");
        strategy.put("description", "回测策略: ma_cross(均线金叉死叉), rsi(RSI超买超卖), macd(MACD策略), bollinger(布林带), grid(网格交易), buy_hold(买入持有基准)。默认 ma_cross。");
        strategy.put("enum", List.of("ma_cross", "rsi", "macd", "bollinger", "grid", "buy_hold"));
        properties.put("strategy", strategy);

        Map<String, Object> capital = new LinkedHashMap<>();
        capital.put("type", "number");
        capital.put("description", "初始资金（元），如 100000 表示10万元。默认 100000。");
        properties.put("capital", capital);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "回测天数（自然日）。30=近一月，90=近三月，180=近半年，365=近一年。默认 180。");
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", List.of("asset"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String asset = getString(arguments, "asset");
        if (asset == null || asset.isBlank()) {
            return ToolResult.text("错误: 请指定要回测的股票或期货（名称或代码）");
        }

        String strategy = getString(arguments, "strategy");
        if (strategy == null || strategy.isBlank()) strategy = "ma_cross";

        double capital = getDouble(arguments, "capital", 100000);
        int days = getInt(arguments, "days", 180);

        log.info("BacktestTool: asset={}, strategy={}, capital={}, days={}", asset, strategy, capital, days);

        BacktestService.BacktestResult result = backtestService.backtest(asset, strategy, capital, days);
        return ToolResult.text(result.toText());
    }

    private String getString(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        return v instanceof String s ? s : (v != null ? v.toString() : null);
    }

    private double getDouble(Map<String, Object> args, String key, double def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) {} }
        return def;
    }

    private int getInt(Map<String, Object> args, String key, int def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) {} }
        return def;
    }
}
