package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FundService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FundTool implements Tool {

    private final FundService fundService;

    public FundTool(FundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public String getName() {
        return "fund";
    }

    @Override
    public String getDescription() {
        return "查询公募基金净值。支持基金名称或代码查询，如：易方达蓝筹精选净值、005827、招商中证白酒";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> code = new LinkedHashMap<>();
        code.put("type", "string");
        code.put("description", "基金名称或代码，如：易方达蓝筹精选、005827、招商中证白酒");
        properties.put("code", code);

        params.put("properties", properties);
        params.put("required", List.of("code"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String code = arguments != null ? (String) arguments.get("code") : null;
        if (code == null || code.isBlank()) {
            return ToolResult.text("请提供基金名称或代码，如：易方达蓝筹精选净值");
        }

        String result = fundService.query(code.trim());
        if (result == null) {
            return ToolResult.text("基金查询失败，请检查名称或代码是否正确");
        }
        return ToolResult.text(result);
    }
}
