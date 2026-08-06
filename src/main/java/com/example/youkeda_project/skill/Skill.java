package com.example.youkeda_project.skill;

import com.example.youkeda_project.tool.Tool;

import java.util.List;

/**
 * Skill interface protocol.
 *
 * A Skill is a pluggable domain capability module that contains:
 * 1. Domain identity: name, description, trigger keywords or intents.
 * 2. Tool set: one or more Tool implementations for Function Calling.
 * 3. Knowledge base: text snippets used for RAG context augmentation.
 *
 * SkillManager dynamically loads and registers Skills by scanning src/skills/{skill-name}/skill.json.
 * No if-else hard-coded routing is required.
 */

public interface Skill {

    /** Unique skill identifier, e.g. financial-analysis. */
    String getName();

    /** Human-readable description used for LLM intent recognition. */
    String getDescription();

    /** Example keywords or sentences that trigger this Skill (used for routing). */
    List<String> getTriggerExamples();

    /** Function Calling tools provided by this Skill. */
    List<Tool> getTools();

    /** Relative paths of knowledge documents under src/skills/{name}/. */
    List<String> getKnowledgeDocuments();
}
