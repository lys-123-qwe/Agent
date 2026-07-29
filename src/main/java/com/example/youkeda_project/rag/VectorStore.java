package com.example.youkeda_project.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    private final EmbeddingService embeddingService;
    private final List<Entry> entries = new ArrayList<>();

    public VectorStore(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        add("股票市盈率（PE）是股价与每股收益的比值，反映市场对股票盈利能力的估值。市净率（PB）是股价与每股净资产的比值，衡量资产价值。A股常见PE范围：大盘蓝筹10-20倍，成长股30-50倍。");
        add("贵州茅台（sh600519）是中国白酒龙头企业，主营茅台酒及系列酒。2026年PE约20倍，总市值约1.6万亿，属于典型的大盘蓝筹消费股。");
        add("移动平均线（MA）是技术分析常用指标。MA5/MA10/MA20分别代表5日/10日/20日收盘价均值。当短期均线上穿长期均线为金叉（看涨信号），下穿为死叉（看跌信号）。");
        add("RSI（相对强弱指标）衡量价格涨跌力度，取值范围0-100。RSI>70为超买（可能回调），RSI<30为超卖（可能反弹）。常用RSI6和RSI12两个周期。");
        add("K线图（蜡烛图）包含四个价格：开盘价、收盘价、最高价、最低价。实体为红色（收盘>开盘）表示上涨，绿色（收盘<开盘）表示下跌。上影线为最高价到实体的距离。");
        add("基金净值（NAV）是基金每份的价值，等于基金资产总值减去负债后除以总份额。ETF可以在盘中实时交易，价格围绕净值波动。开放式基金每日公布一次净值。");
        add("期货是标准化合约，约定在未来某一时间以确定价格买卖标的物。国内期货主力合约代码带0后缀（如RB0表示螺纹钢主力连续），换月时自动切换。");
        add("沪金（nf_AU0）是上海期货交易所的黄金期货主力连续合约。黄金期货受国际金价、美元指数、地缘政治等因素影响，是重要的避险资产。");
        add("螺纹钢（nf_RB0）是上海期货交易所的钢材期货主力连续合约，与房地产和基建投资高度相关，是衡量经济景气度的重要指标。");
        add("投资有风险，入市需谨慎。以上信息仅供参考，不构成投资建议。历史表现不代表未来收益。");
        log.info("知识库初始化完成: {} 条知识", entries.size());
    }

    public void add(String text) {
        entries.add(new Entry(text));
    }

    public void addWithVector(String text, float[] vector) {
        Entry e = new Entry(text);
        e.vector = vector;
        entries.add(e);
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

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-10);
    }

    private static class Entry {
        final String text; float[] vector;
        Entry(String text) { this.text = text; }
    }
    private record Scored(int idx, double score) {}
}
