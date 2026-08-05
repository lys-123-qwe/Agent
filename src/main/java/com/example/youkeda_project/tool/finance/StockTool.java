package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.stock.StockService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 股票查询工具(Function Calling).
 */
@Component
public class StockTool extends AbstractTool {

        private final StockService stockService;

    public StockTool(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public String getName() {
        return "get_stock";
    }

    @Override
    public String getDescription() {
        return "查询A股,港股,美股,日股,韩股的实时行情,包括当前价格,涨跌幅,成交量等."
            + "A股支持股票名称(如:贵州茅台,宁德时代,比亚迪)或代码(如:600519,sh600519)."
            + "港股支持名称(如:腾讯控股,阿里巴巴,美团)或代码(如:00700,hk00700)."
            + "美股支持名称(如:苹果,特斯拉,英伟达)或字母代码(如:AAPL,TSLA,NVDA)."
            + "日股/韩股支持名称(如:丰田,索尼,任天堂,软银,三星电子,SK海力士,现代汽车,LG化学),"
            + "查询时请传中文名称(如'丰田'),不要臆造美股代码(如TM/SSNGY),系统会自动识别为日股/韩股."
            + "也支持大盘指数:上证指数,深证成指,创业板指,科创50,沪深300.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("type", "string");
        stock.put("description", "股票名称或代码(A股/港股/美股/日股/韩股均支持)."
            + "A股:贵州茅台=sh600519,宁德时代=sz300750,比亚迪=sz002594,中国平安=sh601318,招商银行=sh600036."
            + "港股:腾讯控股=hk00700,阿里巴巴=hk09988,美团=hk03690,小米=hk01810,京东=hk09618."
            + "美股:苹果=gb_aapl,特斯拉=gb_tsla,英伟达=gb_nvda,微软=gb_msft,谷歌=gb_goog."
            + "日股:丰田=jp_7203,索尼=jp_6758,任天堂=jp_7974,软银=jp_9984,本田=jp_7267."
            + "韩股:三星电子=kr_005930,SK海力士=kr_000660,现代汽车=kr_005380,LG化学=kr_051910,起亚=kr_000270."
            + "指数:上证指数,深证成指,创业板指,科创50,沪深300."
            + "也支持直接输入代码如 600519,00700,AAPL,sh600519,hk00700,gb_aapl,jp_7203,kr_005930.");
        properties.put("stock", stock);

        params.put("properties", properties);
        params.put("required", java.util.List.of("stock"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String stock = arguments != null ? (String) arguments.get("stock") : null;
        if (stock == null || stock.isBlank()) {
            return ToolResult.text("错误: 请指定股票名称或代码\n"
                + "示例: 贵州茅台 / 600519 / sh600519 / 腾讯 / AAPL");
        }

        log.info("StockTool 执行: stock={}", stock);

        String quote = stockService.queryStock(stock);

        // 附加基本面数据（市盈率/市净率/市值等）
        try {
            StockService.FundamentalData fd = stockService.getFundamentals(stock);
            if (fd != null) {
                StringBuilder sb = new StringBuilder(quote);
                sb.append("\n\n📊 **基本面数据**\n");
                if (fd.pe() != null && !fd.pe().isBlank() && !"—".equals(fd.pe())) {
                    sb.append("· 市盈率(PE)：").append(fd.pe()).append("\n");
                }
                if (fd.pb() != null && !fd.pb().isBlank() && !"—".equals(fd.pb())) {
                    sb.append("· 市净率(PB)：").append(fd.pb()).append("\n");
                }
                if (fd.marketCap() != null && !fd.marketCap().isBlank() && !"—".equals(fd.marketCap())) {
                    sb.append("· 总市值：").append(fd.marketCap()).append("\n");
                }
                if (fd.circulatingCap() != null && !fd.circulatingCap().isBlank() && !"—".equals(fd.circulatingCap())) {
                    sb.append("· 流通市值：").append(fd.circulatingCap()).append("\n");
                }
                return ToolResult.text(sb.toString());
            }
        } catch (Exception e) {
            log.warn("基本面数据获取失败: {}", e.getMessage());
        }

        return ToolResult.text(quote);
    }
}
