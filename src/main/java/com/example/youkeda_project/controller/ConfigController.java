package com.example.youkeda_project.controller;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.rag.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 配置接口：人设、知识库（供 Web 前端左中配置面板使用）。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AIService aiService;
    private final VectorStore vectorStore;

    public ConfigController(AIService aiService, VectorStore vectorStore) {
        this.aiService = aiService;
        this.vectorStore = vectorStore;
    }

    /** 查询用户人设。 */
    @GetMapping("/persona")
    public Map<String, Object> getPersona(@RequestParam(defaultValue = "web") String userId) {
        String p = aiService.getPersona(userId);
        return Map.of("persona", p != null ? p : "");
    }

    /** 设置用户人设（空值则清除）。 */
    @PostMapping("/persona")
    public Map<String, Object> setPersona(@RequestParam(defaultValue = "web") String userId,
                                          @RequestParam String persona) {
        aiService.setPersona(userId, persona);
        return Map.of("ok", true);
    }

    /** 添加一条知识到知识库。 */
    @PostMapping("/knowledge")
    public Map<String, Object> addKnowledge(@RequestParam String text) {
        if (text == null || text.isBlank()) {
            return Map.of("ok", false, "msg", "知识内容不能为空");
        }
        vectorStore.add(text.trim());
        return Map.of("ok", true);
    }
}
