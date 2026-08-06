package com.example.youkeda_project.controller;

import com.example.youkeda_project.ai.AIService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * token 消耗统计接口（供 Web 前端左下角监控面板使用）。
 */
@RestController
@RequestMapping("/api")
public class TokenController {

    private final AIService aiService;

    public TokenController(AIService aiService) {
        this.aiService = aiService;
    }

    /** 按模型 + 按账户返回 token 消耗统计，含总消耗。 */
    @GetMapping("/token-stats")
    public Map<String, Object> stats() {
        Map<String, Object> accounts = new LinkedHashMap<>();
        for (var e : aiService.getTokenUsage().entrySet()) {
            long[] u = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prompt", u[0]);
            m.put("completion", u[1]);
            m.put("total", u[0] + u[1]);
            accounts.put(e.getKey(), m);
        }
        Map<String, Object> models = new LinkedHashMap<>();
        long grandTotal = 0;
        for (var e : aiService.getModelUsage().entrySet()) {
            long[] u = e.getValue();
            long total = u[0] + u[1];
            grandTotal += total;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prompt", u[0]);
            m.put("completion", u[1]);
            m.put("total", total);
            models.put(e.getKey(), m);
        }
        // 工具/服务消耗（翻译、文案、运势、金融分析、embedding 等）
        Map<String, Object> sources = new LinkedHashMap<>();
        for (var e : com.example.youkeda_project.util.TokenStats.snapshot().entrySet()) {
            long[] u = e.getValue();
            long total = u[0] + u[1];
            grandTotal += total;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("prompt", u[0]);
            m.put("completion", u[1]);
            m.put("total", total);
            sources.put(e.getKey(), m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accounts", accounts);
        out.put("models", models);
        out.put("sources", sources);
        out.put("grandTotal", grandTotal);
        return out;
    }
}
