package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.stock.FundService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基金查询工具（Function Calling）。
 */
@Component
public class FundTool extends AbstractTool {

        private final FundService fundService;

    public FundTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "get_fund";
    }

    @Override
    public String getDescription() {
        return "查询基金的实时行情或最新净值。"
            + "ETF（场内基金）: 沪深300ETF(sh510300)、上证50ETF(sh510050)、创业板ETF(sz159915)、"
            + "芯片ETF(sz159995)、新能源ETF(sh516160)、医药ETF(sh512010)、白酒ETF(sz159869)、"
            + "军工ETF(sh512660)、证券ETF(sh512880)、黄金ETF(sh518880)等。"
            + "开放式基金: 易方达蓝筹精选(005827)、中欧医疗健康(003095)、招商中证白酒(161725)、"
            + "景顺长城新兴成长(260108)、富国天惠成长(161005)、兴全合润(163406)、"
            + "天弘沪深300(000961)、余额宝(000198)等。"
            + "支持中文名称（沪深300ETF/易方达蓝筹精选）或代码（510300/005827）。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> fund = new LinkedHashMap<>();
        fund.put("type", "string");
        fund.put("description", "基金名称或代码。"
            + "ETF: 沪深300ETF=sh510300、上证50ETF=sh510050、创业板ETF=sz159915、"
            + "芯片ETF=sz159995、新能源ETF=sh516160、医药ETF=sh512010、白酒ETF=sz159869。"
            + "开放式基金: 易方达蓝筹精选=005827、中欧医疗健康=003095、招商中证白酒=161725。"
            + "也可直接输入代码如 510300、005827。");
        properties.put("fund", fund);

        params.put("properties", properties);
        params.put("required", java.util.List.of("fund"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String fund = arguments != null ? (String) arguments.get("fund") : null;
        if (fund == null || fund.isBlank()) {
            return ToolResult.text("错误: 请指定基金名称或代码\n"
                + "示例: 沪深300ETF / 易方达蓝筹精选 / 510300 / 005827");
        }

        log.info("FundTool 执行: fund={}", fund);

        return ToolResult.text(fundService.queryFund(fund));
    }
}
