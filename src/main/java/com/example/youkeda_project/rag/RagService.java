package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
/** RAG 检索编排：关键词优先、向量检索、未命中先学后查、拼入 prompt。 */


@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 3;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KnowledgeCollector knowledgeCollector;

    public RagService(EmbeddingService embeddingService, VectorStore vectorStore,
                      KnowledgeCollector knowledgeCollector) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.knowledgeCollector = knowledgeCollector;
    }

    @PostConstruct
    /** 启动时打印知识库条数。 */
    public void init() {
        log.info("RAG 初始化完成，知识库条目数: {}", vectorStore.size());
    }

    /** 检索通用知识：关键词优先 → 向量检索 → 未命中先搜网入库再查。 */
    public List<String> retrieve(String query) {
        if (query == null || query.isBlank()) return List.of();
        try {
            // 先用关键词快速匹配，跳过 embedding
            List<String> fastResults = vectorStore.fastSearch(query);
            if (!fastResults.isEmpty()) {
                log.debug("RAG 关键词命中: query=\"{}\", {} 条", query, fastResults.size());
                return fastResults;
            }

            float[] queryVec = embeddingService.embed(query);
            List<String> results = vectorStore.search(queryVec, TOP_K);
            log.debug("RAG 向量检索: query=\"{}\", 命中={}条", query, results.size());
            // 没命中则先搜网入库，再重新检索
            if (results.isEmpty()) {
                List<String> newEntries = knowledgeCollector.learn(query);
                if (!newEntries.isEmpty()) {
                    float[] newVec = embeddingService.embed(query);
                    results = vectorStore.search(newVec, TOP_K);
                    log.debug("RAG 检索(二次): query=\"{}\", 命中={}条", query, results.size());
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("RAG 检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 判断是否为问候等无关紧要的短句（跳过 RAG 检索）。 */
    private static boolean isTrivial(String query) {
        String q = query.trim();
        return q.matches("^(你好|嗨|hi|hello|在吗|在不在|谢谢|感谢|好的|嗯|哦|哈喽|hey|早上好|晚上好|下午好|晚安|拜拜|再见|bye).*");
    }

    // ==================== skill 知识检索（路由命中 skill 时用） ====================

    /**
     * 检索指定 skill 下的知识（关键词优先，未命中走向量），返回拼好的上下文文本。
     * 替代原 skill.KnowledgeService.buildContext 的职责。
     */
    /** 检索指定 skill 的知识并拼成上下文文本（路由命中时用）。 */
    public String buildSkillContext(String skillName, String query) {
        if (skillName == null || query == null || query.isBlank()) return "";
        List<String> chunks = retrieveForSkill(skillName, query);
        if (chunks.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("Relevant knowledge snippets:\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("--- snippet ").append(i + 1).append(" ---\n");
            sb.append(chunks.get(i)).append("\n");
        }
        sb.append("--- end of knowledge snippets ---\n");
        return sb.toString();
    }

    /** 检索指定 skill 下的知识（关键词 → 向量）。 */
    public List<String> retrieveForSkill(String skillName, String query) {
        try {
            List<String> fastResults = vectorStore.fastSearchForSkill(skillName, query);
            if (!fastResults.isEmpty()) return fastResults;
            float[] queryVec = embeddingService.embed(query);
            return vectorStore.searchForSkill(skillName, queryVec, TOP_K);
        } catch (Exception e) {
            log.warn("Skill[{}] 知识检索失败: {}", skillName, e.getMessage());
            return List.of();
        }
    }

    /** 增强系统提示词：把检索到的相关知识拼入 prompt。 */
    public String augment(String query, String systemPrompt) {
        if (isTrivial(query)) return systemPrompt;
        List<String> snippets = retrieve(query);
        if (snippets.isEmpty()) return systemPrompt;
        StringBuilder sb = new StringBuilder(systemPrompt);
        sb.append("\n\n参考以下资料回答用户的问题：\n");
        for (int i = 0; i < snippets.size(); i++) {
            sb.append("---\n").append(snippets.get(i)).append("\n");
        }
        return sb.toString();
    }
}
