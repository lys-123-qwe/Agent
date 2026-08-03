package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.assist.PhoneLocationService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PhoneLocationTool implements Tool {

    private final PhoneLocationService phoneLocationService;

    public PhoneLocationTool(PhoneLocationService phoneLocationService) {
        this.phoneLocationService = phoneLocationService;
    }

    @Override
    public String getName() {
        return "phone_location";
    }

    @Override
    public String getDescription() {
        return "查询手机号码的归属地信息,包括所属省份,城市,运营商(移动/联通/电信)等";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> phone = new LinkedHashMap<>();
        phone.put("type", "string");
        phone.put("description", "手机号码(11位)或手机号前7位");
        properties.put("phone", phone);

        params.put("properties", properties);
        params.put("required", List.of("phone"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null || arguments.get("phone") == null) {
            return ToolResult.text("请提供手机号码");
        }

        String phone = ((String) arguments.get("phone")).trim();
        if (phone.length() < 7) {
            return ToolResult.text("手机号码格式不正确,请输入11位手机号或前7位");
        }

        String result = phoneLocationService.lookup(phone);
        return ToolResult.text(result);
    }
}
