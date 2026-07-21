package com.example.youkeda_project.ai;

/**
 * DeepSeek 意图识别结果。
 * <p>
 * intent 取值：
 * - "chat"：一般对话，reply 为直接回复
 * - "weather"：天气查询，city 为城市名
 * - "image"：图片生成，description 为图片描述
 * - "tts"：语音合成，description 为待合成的文本内容
 */
public record IntentResult(
    String intent,
    String city,
    String description,
    String reply
) {
    public static IntentResult chat(String reply) {
        return new IntentResult("chat", null, null, reply);
    }

    public static IntentResult weather(String city) {
        return new IntentResult("weather", city, null, null);
    }

    public static IntentResult image(String description) {
        return new IntentResult("image", null, description, null);
    }

    public static IntentResult tts(String description) {
        return new IntentResult("tts", null, description, null);
    }
}
