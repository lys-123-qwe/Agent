package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.WechatSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WechatSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WechatSearchTool.class);

    private final WechatSearchService wechatSearchService;

    public WechatSearchTool(WechatSearchService wechatSearchService) {
        this.wechatSearchService = wechatSearchService;
    }

    @Override
    public String getName() {
        return "wechat_search";
    }

    @Override
    public String getDescription() {
        return "搜索微信公众号文章，返回标题、摘要和链接。请直接输出以下内容给用户，不要修改或省略任何部分，特别是链接。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，如'人工智能'、'华为'、'健康'等");
        properties.put("keyword", keyword);

        Map<String, Object> num = new LinkedHashMap<>();
        num.put("type", "integer");
        num.put("description", "返回结果条数，1-10，默认为5");
        properties.put("num", num);

        params.put("properties", properties);
        params.put("required", java.util.List.of("keyword"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        int num = 5;
        String keyword = "";
        if (arguments != null) {
            if (arguments.get("num") instanceof Integer n) {
                num = n;
            }
            if (arguments.get("keyword") instanceof String k) {
                keyword = k;
            }
        }
        log.info("WechatSearchTool 执行: num={}, keyword={}", num, keyword);
        return ToolResult.text(wechatSearchService.search(keyword, num));
    }
}