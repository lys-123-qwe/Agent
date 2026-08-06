package com.example.youkeda_project.skill;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 技能路由管理器。
 *
 * 技能通过 Spring Bean 注册（ToolRegistry.init 收集），此处仅负责：
 * 根据用户输入命中技能触发词，返回匹配的技能（供上层做 RAG 知识检索）。
 */

@Component
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    private final ToolRegistry toolRegistry;

    public SkillManager(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    public void init() {
        log.info("技能框架就绪，注册技能数: {}", toolRegistry.getSkills().size());
    }

    /**
     * 根据用户输入匹配技能，未命中返回空。
     * 采用触发词打分：完整触发词命中强匹配，长词部分匹配加分。
     */
    public Optional<Skill> routeSkill(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String lowerQuery = query.toLowerCase();

        Skill best = null;
        double bestScore = 1.5; // 需要至少一个完整触发词命中或强部分匹配

        for (Skill skill : toolRegistry.getSkills()) {
            double score = matchScore(skill, lowerQuery);
            if (score > bestScore) {
                bestScore = score;
                best = skill;
            }
        }
        return Optional.ofNullable(best);
    }

    private double matchScore(Skill skill, String query) {
        double score = 0;
        java.util.List<String> triggers = skill.getTriggerExamples();
        if (triggers == null) return 0;

        for (String trigger : triggers) {
            String lower = trigger.toLowerCase();
            // 完整触发词命中:强匹配
            if (query.contains(lower)) {
                score += 2.0;
                continue;
            }
            // 部分匹配:仅当触发词较长(>=4字)才给分,避免单字高频字误匹配
            Set<String> a = tokenize(trigger);
            Set<String> b = tokenize(query);
            a.retainAll(b);
            if (a.size() >= 2 && a.size() >= trigger.length() / 2) {
                score += a.size() * 0.3;
            }
        }
        return score;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        StringBuilder word = new StringBuilder();
        for (int cp : text.toLowerCase().codePoints().toArray()) {
            if (isCjkUnifiedIdeograph(cp)) {
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
                tokens.add(new String(Character.toChars(cp)));
            } else if (Character.isLetterOrDigit(cp)) {
                word.appendCodePoint(cp);
            } else {
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
            }
        }
        if (word.length() > 0) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    private boolean isCjkUnifiedIdeograph(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D;
    }
}
