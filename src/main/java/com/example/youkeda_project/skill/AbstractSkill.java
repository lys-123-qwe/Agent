package com.example.youkeda_project.skill;

import com.example.youkeda_project.tool.Tool;
import java.util.List;

/**
 * Abstract base class for Skills providing common implementations.
 */
public abstract class AbstractSkill implements Skill {

    private final String name;
    private final String description;
    private final List<String> triggerExamples;
    private final List<Tool> tools;
    private final List<String> knowledgeDocuments;

    public AbstractSkill(String name, String description,
                         List<String> triggerExamples,
                         List<Tool> tools,
                         List<String> knowledgeDocuments) {
        this.name = name;
        this.description = description;
        this.triggerExamples = triggerExamples != null ? triggerExamples : List.of();
        this.tools = tools != null ? tools : List.of();
        this.knowledgeDocuments = knowledgeDocuments != null ? knowledgeDocuments : List.of();
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public List<String> getTriggerExamples() { return triggerExamples; }

    @Override
    public List<Tool> getTools() { return tools; }

    @Override
    public List<String> getKnowledgeDocuments() { return knowledgeDocuments; }
}
