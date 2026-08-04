package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;

import com.example.youkeda_project.service.stock.MoneyFlowService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资金流向查询工具（Function Calling）。
 * <p>
 * 定义 get_money_flow 函数，供 DeepSeek 通过 Function Calling 调用。
 * 支持行业板块资金排名、个股资金流向、北向资金三种查询模式。
 */
@Component
public class MoneyFlowTool extends AbstractTool {

        private final MoneyFlowService moneyFlowService;

    public MoneyFlowTool(MoneyFlowService moneyFlowService) {
        this.moneyFlowService = moneyFlowService;
    }

    @Override
    public String getName() {
        return "get_money_flow";
    }

    @Override
    public String getDescription() {
        return "查询资金流向数据，支持三种模式："
                + "① sector_ranking：行业板块资金流向排名（主力/超大单/大单净流入），"
                + "用于判断当日哪些板块受到大资金青睐。"
                + "② stock：个股资金流向，查看某只股票近N日主力净流入趋势。"
                + "③ north_bound：北向资金（外资通过沪港通/深港通流入A股），"
                + "是市场情绪和外资态度的重要参考。"
                + "当你需要判断市场资金面、板块轮动、主力动向时必须调用此工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("type", "string");
        mode.put("description", "查询模式："
                + "sector_ranking=行业板块资金流向排名，"
                + "stock=个股资金流向，"
                + "north_bound=北向资金，"
                + "sector_by_name=按名称搜索板块资金流向。"
                + "用户问'主力在买什么板块''资金去哪了'→ sector_ranking；"
                + "用户问'XX股票资金流向''XX主力动向'→ stock；"
                + "用户问'外资''北向资金''沪深股通'→ north_bound");
        mode.put("enum", List.of("sector_ranking", "stock", "north_bound", "sector_by_name"));
        properties.put("mode", mode);

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("type", "string");
        stock.put("description", "股票名称或代码（仅 mode=stock 时需要）。如：贵州茅台、宁德时代、600519。仅支持A股。");
        properties.put("stock", stock);

        Map<String, Object> sector = new LinkedHashMap<>();
        sector.put("type", "string");
        sector.put("description", "板块关键词（仅 mode=sector_by_name 时需要）。如：半导体、新能源、白酒、人工智能。");
        properties.put("sector", sector);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "查询天数。stock/north_bound 模式默认 5 天，最大 30 天。sector_ranking 忽略此参数。");
        properties.put("days", days);

        Map<String, Object> topN = new LinkedHashMap<>();
        topN.put("type", "integer");
        topN.put("description", "返回前N条（仅 sector_ranking 模式有效），默认 15，最大 50。");
        properties.put("topN", topN);

        params.put("properties", properties);
        params.put("required", List.of("mode"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String mode = arguments != null ? (String) arguments.get("mode") : null;
        if (mode == null || mode.isBlank()) {
            return ToolResult.text("错误: 请指定查询模式（sector_ranking/stock/north_bound/sector_by_name）");
        }

        log.info("MoneyFlowTool 执行: mode={}", mode);

        return switch (mode) {
            case "sector_ranking" -> {
                int topN = getIntArg(arguments, "topN", 15);
                yield ToolResult.text(moneyFlowService.getSectorMoneyFlow(topN));
            }
            case "stock" -> {
                String s = (String) arguments.get("stock");
                if (s == null || s.isBlank()) {
                    yield ToolResult.text("错误: 请指定股票名称或代码");
                }
                int d = getIntArg(arguments, "days", 5);
                yield ToolResult.text(moneyFlowService.getStockMoneyFlow(s, d));
            }
            case "north_bound" -> {
                int d = getIntArg(arguments, "days", 10);
                yield ToolResult.text(moneyFlowService.getNorthBoundFlow(d));
            }
            case "sector_by_name" -> {
                String kw = (String) arguments.get("sector");
                if (kw == null || kw.isBlank()) {
                    yield ToolResult.text("错误: 请指定板块关键词");
                }
                int n = getIntArg(arguments, "topN", 20);
                yield ToolResult.text(moneyFlowService.getSectorByName(kw, n));
            }
            default -> ToolResult.text("错误: 不支持的查询模式: " + mode);
        };
    }

    private int getIntArg(Map<String, Object> args, String key, int defaultVal) {
        if (args == null) return defaultVal;
        Object val = args.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}
