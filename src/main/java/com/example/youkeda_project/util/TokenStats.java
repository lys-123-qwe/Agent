package com.example.youkeda_project.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 全局 token 消耗统计（按来源：工具/服务），记录对话之外的 AI 接口消耗，供 Web 展示。 */
public final class TokenStats {

    private TokenStats() {}

    private static final Map<String, long[]> USAGE = new ConcurrentHashMap<>();
    /** 生图/改图/语音等按次计费统计: source -> 次数 */
    private static final Map<String, Long> COUNTS = new ConcurrentHashMap<>();

    /** 记录一次调用的 token 消耗。source 建议用模型名或服务名（如 qwen-turbo / 金融分析 / embedding）。 */
    public static void record(String source, long promptTokens, long completionTokens) {
        long[] u = USAGE.computeIfAbsent(source, k -> new long[2]);
        u[0] += promptTokens;
        u[1] += completionTokens;
    }

    /** 记录一次按次计费的服务调用（生图/改图/语音合成/语音识别等）。source 如 文生图/图编辑/语音合成/语音识别。 */
    public static void recordCount(String source) {
        COUNTS.merge(source, 1L, Long::sum);
    }

    /** 兼容旧方法名：图片生成/编辑次数。 */
    public static void recordImage(String source) {
        recordCount(source);
    }

    /** 返回各来源消耗快照（source -> [prompt, completion]）。 */
    public static Map<String, long[]> snapshot() {
        return new ConcurrentHashMap<>(USAGE);
    }

    /** 返回按次计费服务调用次数快照（source -> 次数）。 */
    public static Map<String, Long> countSnapshot() {
        return new ConcurrentHashMap<>(COUNTS);
    }
}
