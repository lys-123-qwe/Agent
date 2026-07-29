package com.example.youkeda_project;

import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.skill.Skill;
import com.example.youkeda_project.skill.SkillManager;
import com.example.youkeda_project.skill.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkillSetupVerificationTest {

    @Autowired
    private SkillManager skillManager;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void skillManagerHasFinancialAnalysis() {
        List<Skill> skills = toolRegistry.getSkills();
        assertFalse(skills.isEmpty(), "至少要加载一个 Skill");
        Optional<Skill> fin = skills.stream()
                .filter(s -> "financial-analysis".equals(s.getName()))
                .findFirst();
        assertTrue(fin.isPresent(), "必须加载 financial-analysis skill");
    }

    @Test
    void financialAnalysisSkillCanRoute() {
        Optional<Skill> routed = skillManager.routeSkill("分析今天 BTC 走势");
        assertTrue(routed.isPresent(), "BTC 分析请求应该被路由到某个 Skill");
        assertEquals("financial-analysis", routed.get().getName(), "应该路由到 financial-analysis");
    }

    @Test
    void financialAnalysisHasTools() {
        List<Tool> tools = toolRegistry.getToolsForSkill("financial-analysis");
        assertFalse(tools.isEmpty(), "financial-analysis 至少要暴露一个工具");
    }

    @Test
    void toolRegistryHasAllTools() {
        List<Tool> all = toolRegistry.allTools();
        assertTrue(all.size() >= 18, "工具总数应该 >= 18 (原基础工具 + 金融分析工具)");
    }
}
