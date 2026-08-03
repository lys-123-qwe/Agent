package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.stock.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 股票查询工具(Function Calling).
 */
@Component
public class StockTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(StockTool.class);

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
        return "查询A股,港股,美股的实时行情,包括当前价格,涨跌幅,成交量等."
            + "A股支持股票名称(如:贵州茅台,宁德时代,比亚迪)或代码(如:600519,sh600519)."
            + "港股支持名称(如:腾讯控股,阿里巴巴,美团)或代码(如:00700,hk00700)."
            + "美股支持名称(如:苹果,特斯拉,英伟达)或字母代码(如:AAPL,TSLA,NVDA)."
            + "也支持大盘指数:上证指数,深证成指,创业板指,科创50,沪深300.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("type", "string");
        stock.put("description", "股票名称或代码(A股/港股/美股均支持)."
            + "A股:贵州茅台=sh600519,宁德时代=sz300750,比亚迪=sz002594,中国平安=sh601318,招商银行=sh600036."
            + "港股:腾讯控股=hk00700,阿里巴巴=hk09988,美团=hk03690,小米=hk01810,京东=hk09618."
            + "美股:苹果=gb_aapl,特斯拉=gb_tsla,英伟达=gb_nvda,微软=gb_msft,谷歌=gb_goog."
            + "指数:上证指数,深证成指,创业板指,科创50,沪深300."
            + "也支持直接输入代码如 600519,00700,AAPL,sh600519,hk00700,gb_aapl.");
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
        return ToolResult.text(quote);
    }
}
