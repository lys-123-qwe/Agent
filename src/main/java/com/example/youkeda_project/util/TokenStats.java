package com.example.youkeda_project.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 全局 token 消耗统计（按来源：工具/服务），记录对话之外的 AI 接口消耗，供 Web 展示。 */
public final class TokenStats {

    private TokenStats() {}

    private static final Map<String, long[]> USAGE = new ConcurrentHashMap<>();

    /** 记录一次调用的 token 消耗。source 建议用模型名或服务名（如 qwen-turbo / 金融分析 / embedding）。 */
    public static void record(String source, long promptTokens, long completionTokens) {
        long[] u = USAGE.computeIfAbsent(source, k -> new long[2]);
        u[0] += promptTokens;
        u[1] += completionTokens;
    }

    /** 返回各来源消耗快照（source -> [prompt, completion]）。 */
    public static Map<String, long[]> snapshot() {
        return new ConcurrentHashMap<>(USAGE);
    }
}
