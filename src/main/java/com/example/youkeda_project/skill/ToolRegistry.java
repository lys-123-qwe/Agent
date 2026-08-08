package com.example.youkeda_project.skill;

import com.example.youkeda_project.tool.Tool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *      .
 *
 *          Skill     Tool,         .
 *                Function Calling   .
 * 
 *        Spring         Tool   Skill Bean,
 *              (  SkillManager   ).
 */

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ApplicationContext applicationContext;

    /**       (     ) */
    private final Map<String, Tool> standaloneTools = new ConcurrentHashMap<>();

    /**   Skill       */
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        applicationContext.getBeansOfType(Tool.class).values().forEach(this::register);
        applicationContext.getBeansOfType(Skill.class).values().forEach(this::register);
    }

    /**
     *         (      :  ,   ).
     */
    public void register(Tool tool) {
        if (tool == null) return;
        standaloneTools.put(tool.getName(), tool);
    }

    /**
     *      Skill,           .   Skill       .
     */
    public void register(Skill skill) {
        if (skill == null) return;
        if (skills.containsKey(skill.getName())) return;
        skills.put(skill.getName(), skill);
    }

    /**
     *          (   + Skill  ).
     */
    public List<Tool> allTools() {
        Map<String, Tool> unique = new LinkedHashMap<>();
        for (Tool t : standaloneTools.values()) {
            unique.putIfAbsent(t.getName(), t);
        }
        for (Skill skill : skills.values()) {
            for (Tool t : skill.getTools()) {
                unique.putIfAbsent(t.getName(), t);
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     *          Skill.
     */
    public List<Skill> getSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     *      Skill    .
     */
    public List<Tool> getToolsForSkill(String skillName) {
        Skill skill = skills.get(skillName);
        if (skill == null) return List.of();
        return new ArrayList<>(skill.getTools());
    }
}
