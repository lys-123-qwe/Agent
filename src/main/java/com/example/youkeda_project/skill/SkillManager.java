package com.example.youkeda_project.skill;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * SkillManager      .
 *
 *    src/skills/*   ,        Skill:
 * 1.           skill.json
 * 2.    skillClass   ,     
 * 3.     ToolRegistry
 * 4.           KnowledgeService
 *
 *    if-else    ,     Skill        +   +     .
 * 
 *       Spring         Skill Bean,           Spring   Skill.
 */
@Component
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    private final ToolRegistry toolRegistry;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    private final Path skillRoot;

    public SkillManager(ToolRegistry toolRegistry, KnowledgeService knowledgeService,
                        ObjectMapper objectMapper, ApplicationContext applicationContext) {
        this.toolRegistry = toolRegistry;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
        this.skillRoot = Paths.get("src", "skills").toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        // 1.     Spring     Skill Bean(         )
        applicationContext.getBeansOfType(Skill.class).values().forEach(this::registerSkill);
        // 2.        ,    Spring   Skill
        loadAllSkillsFromFile();
    }

    private void registerSkill(Skill skill) {
        toolRegistry.register(skill);
        knowledgeService.loadSkillDocuments(skill.getName(), skill.getKnowledgeDocuments());
        log.info("  Spring    Skill: {},  : {},  : {}",
                skill.getName(), skill.getTools().size(), skill.getKnowledgeDocuments());
    }

    /**
     *              Skill.
     */
    private void loadAllSkillsFromFile() {
        if (!Files.isDirectory(skillRoot)) {
            log.warn("Skill      : {},      ", skillRoot);
            return;
        }

        try (Stream<Path> stream = Files.list(skillRoot)) {
            stream.filter(Files::isDirectory)
                    .forEach(this::loadSkillFromDirectory);
        } catch (IOException e) {
            log.error("   Skill     : {}", e.getMessage(), e);
        }

        log.info("Skill       ,    {}   Skill,{}    ",
                toolRegistry.getSkills().size(),
                toolRegistry.allTools().size());
    }

    private void loadSkillFromDirectory(Path dir) {
        Path configFile = dir.resolve("skill.json");
        if (!Files.isRegularFile(configFile)) {
            log.debug("   {}    skill.json,  ", dir.getFileName());
            return;
        }

        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);

            String skillClass = root.path("skillClass").asText(null);
            if (skillClass == null || skillClass.isBlank()) {
                log.warn("{}    skillClass   ", configFile);
                return;
            }

            Class<? extends Skill> clazz = Class.forName(skillClass).asSubclass(Skill.class);
            Skill skill = instantiateSkill(clazz, root);
            if (skill == null) {
                log.warn("      Skill: {}", skillClass);
                return;
            }

            //       Spring      ,  
            if (toolRegistry.getSkills().stream().anyMatch(s -> s.getName().equals(skill.getName()))) {
                log.debug("Skill {}    Spring   ,      ", skill.getName());
                return;
            }

            toolRegistry.register(skill);
            knowledgeService.loadSkillDocuments(skill.getName(), skill.getKnowledgeDocuments());

            log.info("    Skill: {} ({}),  : {},  : {}",
                    skill.getName(), skillClass, skill.getTools().size(), skill.getKnowledgeDocuments());

        } catch (Exception e) {
            log.error("   Skill   : {} - {}", configFile, e.getMessage(), e);
        }
    }

    /**
     *     Skill.    Spring      Bean,     .
     */
    @SuppressWarnings("unchecked")
    private Skill instantiateSkill(Class<? extends Skill> clazz, JsonNode config) {
        try {
            //    Spring        ,    
            Map<String, Skill> beans = (Map<String, Skill>) applicationContext.getBeansOfType(clazz);
            if (!beans.isEmpty()) {
                return beans.values().iterator().next();
            }
        } catch (Exception e) {
            log.debug("    Spring       Skill Bean: {}", clazz.getName());
        }

        try {
            return clazz.getDeclaredConstructor(ObjectMapper.class).newInstance(objectMapper);
        } catch (Exception e) {
            log.debug("  ObjectMapper    : {},      ", clazz.getName());
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("Skill      : {} - {}", clazz.getName(), e.getMessage());
            return null;
        }
    }

    /**
     *          ,       Skill.
     *         +        ,     .
     */
    public Optional<Skill> routeSkill(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String lowerQuery = query.toLowerCase();

        Skill best = null;
        double bestScore = 0.3; //     

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
        List<String> triggers = skill.getTriggerExamples();
        if (triggers == null) return 0;

        for (String trigger : triggers) {
            String lower = trigger.toLowerCase();
            if (query.contains(lower)) {
                score += 1.0;
            } else {
                Set<String> a = tokenize(trigger);
                Set<String> b = tokenize(query);
                a.retainAll(b);
                score += a.size() * 0.2;
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
