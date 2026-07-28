package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FuturesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 期货查询工具（Function Calling）。
 * <p>
 * 定义 get_futures 函数，供 DeepSeek 通过 Function Calling 调用。
 * 委托 FuturesService 通过新浪/腾讯公开接口查询国际/国内期货行情。
 */
@Component
public class FuturesTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FuturesTool.class);

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
        return "查询国际/国内期货的实时行情，包括最新价、涨跌幅、开盘价、最高价、最低价等。"
            + "国际期货: 原油(hf_CL)、黄金(hf_GC)、白银(hf_SI)、铜(hf_HG)、天然气(hf_NG)、"
            + "玉米(hf_ZC)、小麦(hf_ZW)、大豆(hf_ZS)、棉花(hf_CT)、糖(hf_SB)、铂金(hf_PL)、钯金(hf_PA)。"
            + "国内期货: 螺纹钢(nf_RB0)、热卷(nf_HC0)、铁矿石(nf_I0)、焦炭(nf_J0)、焦煤(nf_JM0)、"
            + "沪铜(nf_CU0)、沪铝(nf_AL0)、沪锌(nf_ZN0)、沪镍(nf_NI0)、不锈钢(nf_SS0)、"
            + "原油(nf_SC0)、甲醇(nf_MA0)、PTA(nf_TA0)、纯碱(nf_SA0)、玻璃(nf_FG0)、"
            + "乙二醇(nf_EG0)、苯乙烯(nf_EB0)、PVC(nf_V0)、橡胶(nf_RU0)、纸浆(nf_SP0)、"
            + "豆粕(nf_A0)、棕榈油(nf_P0)、菜油(nf_OI0)、玉米(nf_C0)、"
            + "白糖(nf_SR0)、棉花(nf_CF0)、苹果(nf_AP0)、生猪(nf_LH0)、鸡蛋(nf_JD0)、花生(nf_PK0)。"
            + "股指期货: 沪深300(nf_IF0)、中证500(nf_IC0)、上证50(nf_IH0)、中证1000(nf_IM0)。"
            + "国债期货: 十年国债(nf_T0)、五年国债(nf_TF0)、两年国债(nf_TS0)。"
            + "支持中文名称（原油/黄金/螺纹钢）、代码（hf_CL/nf_RB0）或简称（CL/RB0）。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> futures = new LinkedHashMap<>();
        futures.put("type", "string");
        futures.put("description", "期货名称或代码。"
            + "中文名称: 原油、黄金、白银、螺纹钢、铁矿石、焦炭、沪铜、甲醇、PTA、纯碱、玻璃、"
            + "豆粕、棕榈油、玉米、白糖、棉花、苹果、生猪、鸡蛋、沪深300、中证500等。"
            + "国际代码: hf_CL(原油)、hf_GC(黄金)、hf_SI(白银)、hf_HG(铜)、hf_NG(天然气)等。"
            + "国内代码: nf_RB0(螺纹钢)、nf_I0(铁矿石)、nf_J0(焦炭)、nf_CU0(沪铜)等。"
            + "简称: CL(原油)、GC(黄金)、RB0(螺纹钢)、IF0(沪深300)等自动识别。");
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
        return ToolResult.text(futuresService.queryFutures(futures));
    }
}
