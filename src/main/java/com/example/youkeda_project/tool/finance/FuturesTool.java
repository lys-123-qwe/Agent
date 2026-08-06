package com.example.youkeda_project.tool.finance;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.stock.FuturesService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 期货查询工具（Function Calling）。
 */

@Component
public class FuturesTool extends AbstractTool {

        private final FuturesService futuresService;

    public FuturesTool(FuturesService futuresService) {
        this.futuresService = futuresService;
    }

    @Override
    public String getName() {
        return "get_futures";
    }

    @Override
    public String getDescription() {
        return "查询国际/国内期货。传品种名(如'螺纹钢'/'原油')会返回该品种在交易合约列表(含主力/次主力标注),"
            + "用户选定合约后再传具体合约代码(如'RB2610'/'CL2610')查看该合约行情;传连续合约代码(RB0/CL)直接查主力行情。"
            + "国内期货: 螺纹钢、热卷、铁矿石、焦炭、焦煤、沪铜、沪铝、沪金、沪银、原油、甲醇、PTA、纯碱、玻璃、"
            + "豆粕、棕榈油、玉米、白糖、棉花、苹果、生猪、鸡蛋、沪深300、中证500等。"
            + "国际期货: 原油(CL)、黄金(GC)、白银(SI)、铜(HG)、天然气(NG)、玉米(ZC)、小麦(ZW)、大豆(ZS)、棉花(CT)等。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> futures = new LinkedHashMap<>();
        futures.put("type", "string");
        futures.put("description", "期货名称或代码。"
            + "中文: 原油、黄金、白银、螺纹钢、铁矿石、焦炭、沪铜、甲醇、PTA、纯碱、玻璃、"
            + "豆粕、棕榈油、玉米、白糖、棉花、苹果、生猪、鸡蛋、沪深300等。"
            + "国际代码: hf_CL(原油)、hf_GC(黄金)、hf_SI(白银)等。"
            + "国内代码: nf_RB0(螺纹钢)、nf_I0(铁矿石)、nf_J0(焦炭)等。"
            + "简称: CL(原油)、GC(黄金)、RB0(螺纹钢)等自动识别。");
        properties.put("futures", futures);

        params.put("properties", properties);
        params.put("required", java.util.List.of("futures"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String futures = arguments != null ? (String) arguments.get("futures") : null;
        if (futures == null || futures.isBlank()) {
            return ToolResult.text("错误: 请指定期货名称或代码\n"
                + "示例: 原油 / 黄金 / 螺纹钢 / hf_CL / CL");
        }

        log.info("FuturesTool 执行: futures={}", futures);

        // 判断是具体合约代码(带4位年月,如 RB2610)还是品种名
        String trimmed = futures.trim();
        String upper = trimmed.toUpperCase();
        boolean isSpecificContract = upper.matches("^(?:NF_|HF_)?[A-Z]{1,3}\\d{4}$");

        // 连续合约(如 RB0/nf_RB0)直接查行情
        boolean isContinuous = upper.matches("^(?:NF_|HF_)?[A-Z]{1,3}0$");

        if (isSpecificContract || isContinuous) {
            // 具体合约或连续合约:直接查行情
            return ToolResult.text(futuresService.queryFutures(futures));
        }

        // 品种名:先返回合约列表,让用户选择
        String list = futuresService.listContracts(futures);
        if (list != null && !list.isBlank()) {
            return ToolResult.text(list);
        }

        // 无法识别或列表为空:回退到主力连续查询
        return ToolResult.text(futuresService.queryFutures(futures));
    }
}
