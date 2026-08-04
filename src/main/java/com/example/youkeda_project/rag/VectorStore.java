package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一向量知识库：
 * <ul>
 *   <li>通用知识：加载 resources/knowledge.json，skillName 为空</li>
 *   <li>skill 知识：启动时扫描 src/skills/{skill}/knowledge/*.md 分块入库，skillName=skill 名</li>
 * </ul>
 * 检索分两路：search/fastSearch 只查通用知识；searchForSkill/fastSearchForSkill 只查指定 skill。
 */
@Component
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);
    private static final Set<String> KEYWORDS = Set.of(
        "pe", "市盈率", "pb", "市净率", "ma", "移动平均线", "均线", "金叉", "死叉",
        "rsi", "k线", "蜡烛图", "macd", "布林带", "boll", "kdj",
        "etf", "lof", "基金", "净值", "nav",
        "期货", "期权", "合约", "保证金", "杠杆", "交割",
        "a股", "创业板", "科创板", "新三板", "北交所",
        "上证", "深证", "指数", "沪深300",
        "换手率", "成交量", "量比", "量价",
        "主力资金", "北向资金", "南向资金",
        "限售股", "解禁", "st", "退市",
        "除权", "除息", "填权", "贴权",
        "集合竞价", "连续竞价", "涨跌停", "涨停", "跌停",
        "茅台", "贵州茅台", "五粮液", "白酒",
        "宁德时代", "比亚迪", "新能源",
        "黄金", "原油", "沪金", "螺纹钢",
        "cpi", "ppi", "gdp", "lpr", "利率",
        "美元", "汇率", "人民币",
        "国债", "逆回购", "收益率",
        "t0", "t1", "做空", "融资融券", "融券", "熔断", "t+0", "t+1",
        "营收", "净利润", "现金流", "资产负债表", "资产负债", "审计",
        "大盘", "牛市", "熊市", "板块", "题材", "龙头", "妖股",
        "腾讯", "阿里巴巴", "阿里", "中国平安", "工商银行", "招商银行", "中国银行",
        "建设银行", "农业银行", "中国石油", "中国移动", "平安银行",
        "半导体", "芯片", "光伏", "医药", "银行股", "券商", "白酒股",
        "分时", "均价", "委比", "外盘", "内盘", "振幅",
        "定投", "止盈", "止损", "仓位", "建仓", "加仓", "减仓", "清仓"
    );

    /** 分块参数 */
    private static final int CHUNK_SIZE = 400;
    private static final int CHUNK_OVERLAP = 50;

    private final EmbeddingService embeddingService;
    private final KnowledgeRepository knowledgeRepo;
    private final List<Entry> entries = new ArrayList<>();
    /** 关键词→匹配的知识条目索引 */
    private Map<String, List<Integer>> keywordIndex;

    public VectorStore(EmbeddingService embeddingService, KnowledgeRepository knowledgeRepo) {
        this.embeddingService = embeddingService;
        this.knowledgeRepo = knowledgeRepo;
    }

    @PostConstruct
    public void init() {
        // 清理历史误入库的 topics.md 条目（topics 是预搜主题，非知识）
        knowledgeRepo.deleteBySource("topics.md");

        // 1) 通用知识：扫描 src/knowledge/*.md（每行一条）写入 SQLite
        loadGeneralDocumentsFromDisk();

        // 2) skill 知识：扫描 src/skills/{skill}/knowledge/*.md 分块入库
        loadSkillDocumentsFromDisk();

        // 3) 从 SQLite 加载全部知识到内存（通用 + 各 skill）
        loadAllIntoMemory();

        // 保底知识
        if (entries.isEmpty()) {
            String fallback = "投资有风险，入市需谨慎。以上信息仅供参考，不构成投资建议。历史表现不代表未来收益。";
            entries.add(new Entry(fallback, null));
            knowledgeRepo.save(fallback, "fallback");
        }

        // 4) 构建关键词索引
        buildKeywordIndex();
        int cached = (int) entries.stream().filter(e -> e.vector != null).count();
        log.info("知识库初始化完成: {} 条知识(含 skill 文档), {} 条已有向量缓存", entries.size(), cached);

        // 5) 后台预计算缺失的 embedding
        if (cached < entries.size()) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                int total = entries.size() - cached;
                log.info("开始后台预计算 {} 条知识的 embedding...", total);
                int done = 0;
                for (Entry e : entries) {
                    if (e.vector == null) {
                        try {
                            e.vector = embeddingService.embed(e.text);
                            knowledgeRepo.updateVector(e.text, e.vector);
                            done++;
                        } catch (Exception ex) {
                            log.warn("embedding 预计算失败: {}", ex.getMessage());
                        }
                    }
                }
                log.info("后台预计算完成: {}/{} 条", done, total);
            });
        }
    }

    /** 扫描 src/knowledge/*.md 作为通用知识（每行一条，skill_name 为空）写入 SQLite。topics.md 是预搜主题，非知识，跳过。 */
    private void loadGeneralDocumentsFromDisk() {
        Path generalRoot = Paths.get("src", "knowledge").toAbsolutePath().normalize();
        if (!Files.isDirectory(generalRoot)) return;
        try (Stream<Path> files = Files.list(generalRoot)) {
            files.filter(p -> p.toString().endsWith(".md"))
                .filter(p -> !p.getFileName().toString().equalsIgnoreCase("topics.md"))
                .sorted()
                .forEach(p -> {
                    try {
                        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                            .map(String::trim)
                            .filter(l -> !l.isEmpty())
                            .toList();
                        if (!lines.isEmpty()) {
                            knowledgeRepo.saveBatch(lines, p.getFileName().toString(), null);
                            log.info("通用知识加载: {} 条 (来源 {})", lines.size(), p.getFileName());
                        }
                    } catch (Exception e) {
                        log.warn("读取通用知识文档失败: {}", p);
                    }
                });
        } catch (Exception e) {
            log.warn("扫描 src/knowledge 失败: {}", e.getMessage());
        }
    }

    /** 扫描 src/skills 各技能目录下的 knowledge 文档作为 skill 知识写入 SQLite */
    private void loadSkillDocumentsFromDisk() {
        Path skillsRoot = Paths.get("src", "skills").toAbsolutePath().normalize();
        if (!Files.isDirectory(skillsRoot)) return;
        try (Stream<Path> dirs = Files.list(skillsRoot)) {
            dirs.filter(Files::isDirectory).forEach(skillDir -> {
                String skillName = skillDir.getFileName().toString();
                Path knowledgeDir = skillDir.resolve("knowledge");
                if (!Files.isDirectory(knowledgeDir)) return;
                List<String> chunks = new ArrayList<>();
                try (Stream<Path> files = Files.list(knowledgeDir)) {
                    files.filter(p -> p.toString().endsWith(".md"))
                        .sorted()
                        .forEach(p -> {
                            try {
                                String text = Files.readString(p, StandardCharsets.UTF_8);
                                chunks.addAll(splitIntoChunks(text));
                            } catch (Exception e) {
                                log.warn("读取 skill 文档失败: {}", p);
                            }
                        });
                } catch (Exception e) {
                    log.warn("读取 skill 知识目录失败: {}", knowledgeDir);
                }
                if (!chunks.isEmpty()) {
                    knowledgeRepo.saveBatch(chunks, "skill:" + skillName, skillName);
                    log.info("Skill[{}] 加载 {} 个知识分块", skillName, chunks.size());
                }
            });
        } catch (Exception e) {
            log.warn("扫描 src/skills 失败: {}", e.getMessage());
        }
    }

    /** 从 SQLite 加载全部知识到内存（含向量缓存） */
    private void loadAllIntoMemory() {
        // 通用知识
        loadEntries(knowledgeRepo.findAllWithVector(null), knowledgeRepo.findAll(null), null);
        // 各 skill 知识
        Set<String> skillNames = new HashSet<>();
        try (Stream<Path> dirs = Files.list(Paths.get("src", "skills").toAbsolutePath().normalize())) {
            dirs.filter(Files::isDirectory).forEach(d -> skillNames.add(d.getFileName().toString()));
        } catch (Exception ignored) {}
        for (String skillName : skillNames) {
            loadEntries(knowledgeRepo.findAllWithVector(skillName), knowledgeRepo.findAll(skillName), skillName);
        }
    }

    private void loadEntries(List<KnowledgeRepository.KnowledgeEntry> withVector, List<String> all, String skillName) {
        Map<String, float[]> vectorMap = new HashMap<>();
        for (var entry : withVector) vectorMap.put(entry.text(), entry.vector());
        for (String text : all) {
            Entry e = new Entry(text, skillName);
            e.vector = vectorMap.get(text);
            entries.add(e);
        }
    }

    /** 新增一条通用知识（学后入库用） */
    public void add(String text) {
        entries.add(new Entry(text, null));
        knowledgeRepo.save(text, "collector");
    }

    public void addWithVector(String text, float[] vector) {
        Entry e = new Entry(text, null);
        e.vector = vector;
        entries.add(e);
    }

    // ==================== 通用知识检索 ====================

    /** 先按关键词快速匹配通用知识，命中则直接返回，不走 embedding */
    public List<String> fastSearch(String query) {
        return fastSearch(query, null);
    }

    public List<String> search(float[] queryVec, int topK) {
        return search(queryVec, topK, null);
    }

    // ==================== skill 知识检索 ====================

    public List<String> fastSearchForSkill(String skillName, String query) {
        return fastSearch(query, skillName);
    }

    public List<String> searchForSkill(String skillName, float[] queryVec, int topK) {
        return search(queryVec, topK, skillName);
    }

    /** 检索指定 skill（skillName 为 null 表示通用知识） */
    private List<String> fastSearch(String query, String skillName) {
        String q = query.toLowerCase();
        Set<Integer> matched = new LinkedHashSet<>();
        for (Map.Entry<String, List<Integer>> e : keywordIndex.entrySet()) {
            if (q.contains(e.getKey())) {
                for (int idx : e.getValue()) {
                    if (matchesSkill(entries.get(idx), skillName)) matched.add(idx);
                }
            }
        }
        if (matched.isEmpty()) return List.of();
        return matched.stream().limit(2).map(i -> entries.get(i).text).toList();
    }

    private List<String> search(float[] queryVec, int topK, String skillName) {
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!matchesSkill(e, skillName)) continue;
            if (e.vector == null) {
                e.vector = embeddingService.embed(e.text);
            }
            scored.add(new Scored(i, EmbeddingService.cosineSimilarity(queryVec, e.vector)));
        }
        scored.sort(Comparator.<Scored>comparingDouble(s -> s.score).reversed());
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(entries.get(scored.get(i).idx).text);
        }
        return results;
    }

    private boolean matchesSkill(Entry e, String skillName) {
        if (skillName == null) return e.skillName == null;
        return skillName.equals(e.skillName);
    }

    public int size() { return entries.size(); }

    private void buildKeywordIndex() {
        keywordIndex = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            String text = entries.get(i).text.toLowerCase();
            for (String kw : KEYWORDS) {
                if (text.contains(kw)) {
                    keywordIndex.computeIfAbsent(kw, k -> new ArrayList<>()).add(i);
                }
            }
        }
    }

    /** 将 markdown 文档按段落分块（400字/块，50字重叠） */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : text.split("\n\s*\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;
            if (buffer.length() + trimmed.length() + 2 > CHUNK_SIZE && buffer.length() > 0) {
                chunks.add(buffer.toString().trim());
                String tail = getOverlapTail(buffer.toString());
                buffer.setLength(0);
                buffer.append(tail);
            }
            buffer.append(trimmed).append("\n\n");
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString().trim());
        }
        return chunks;
    }

    private String getOverlapTail(String text) {
        if (text.length() <= CHUNK_OVERLAP) return "";
        return text.substring(Math.max(0, text.length() - CHUNK_OVERLAP));
    }

    private static class Entry {
        final String text;
        final String skillName;
        float[] vector;
        Entry(String text, String skillName) { this.text = text; this.skillName = skillName; }
    }
    private record Scored(int idx, double score) {}
}
