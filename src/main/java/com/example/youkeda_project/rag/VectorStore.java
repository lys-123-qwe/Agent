package com.example.youkeda_project.rag;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

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
        // 1) 将 knowledge.json 的基础知识写入 SQLite（已存在则忽略）
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(
                new ClassPathResource("knowledge.json").getInputStream());
            List<String> loaded = mapper.convertValue(root.get("entries"),
                new TypeReference<List<String>>() {});
            knowledgeRepo.saveBatch(loaded, "knowledge.json");
        } catch (Exception e) {
            log.error("加载 knowledge.json 失败", e);
        }

        // 2) 从 SQLite 加载全部知识到内存（优先加载已有向量的条目）
        java.util.Map<String, float[]> vectorMap = new java.util.HashMap<>();
        for (var entry : knowledgeRepo.findAllWithVector()) {
            vectorMap.put(entry.text(), entry.vector());
        }
        List<String> all = knowledgeRepo.findAll();
        for (String text : all) {
            Entry e = new Entry(text);
            e.vector = vectorMap.get(text);
            entries.add(e);
        }
        // 3) 构建关键词索引
        buildKeywordIndex();
        int cached = (int) entries.stream().filter(e -> e.vector != null).count();
        log.info("知识库初始化完成: {} 条知识, {} 条已有向量缓存", entries.size(), cached);

        // 保底知识
        if (entries.isEmpty()) {
            String fallback = "投资有风险，入市需谨慎。以上信息仅供参考，不构成投资建议。历史表现不代表未来收益。";
            entries.add(new Entry(fallback));
            knowledgeRepo.save(fallback, "fallback");
        }

        // 后台预计算缺失的 embedding，并持久化到 SQLite
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

    public void add(String text) {
        entries.add(new Entry(text));
        knowledgeRepo.save(text, "collector");
    }

    public void addWithVector(String text, float[] vector) {
        Entry e = new Entry(text);
        e.vector = vector;
        entries.add(e);
    }

    /** 先按关键词快速匹配，命中则直接返回，不走 embedding */
    public List<String> fastSearch(String query) {
        String q = query.toLowerCase();
        Set<Integer> matched = new LinkedHashSet<>();
        for (Map.Entry<String, List<Integer>> e : keywordIndex.entrySet()) {
            if (q.contains(e.getKey())) {
                matched.addAll(e.getValue());
            }
        }
        if (matched.isEmpty()) return List.of();
        return matched.stream().limit(2).map(i -> entries.get(i).text).toList();
    }

    public List<String> search(float[] queryVec, int topK) {
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.vector == null) {
                e.vector = embeddingService.embed(e.text);
            }
            scored.add(new Scored(i, cosineSimilarity(queryVec, e.vector)));
        }
        scored.sort(Comparator.<Scored>comparingDouble(s -> s.score).reversed());
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(entries.get(scored.get(i).idx).text);
        }
        return results;
    }

    public int size() { return entries.size(); }

    public double similarityTo(int index, float[] queryVec) {
        if (index < 0 || index >= entries.size()) return 0;
        Entry e = entries.get(index);
        if (e.vector == null) {
            e.vector = embeddingService.embed(e.text);
        }
        return cosineSimilarity(queryVec, e.vector);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-10);
    }

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

    private static class Entry {
        final String text; float[] vector;
        Entry(String text) { this.text = text; }
    }
    private record Scored(int idx, double score) {}
}
