package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音合成工具（Function Calling）。
 * <p>
 * 定义 text_to_speech 函数，供 DeepSeek 通过 Function Calling 调用。
 * 委托 TtsService 调用 CosyVoice 合成语音，返回音频字节供 CDN 上传。
 * <p>
 * voice 参数可选，默认使用"热情女声"。
 * 用户可一次指定文本和音色，也可让模型单独追问。
 */
@Component
public class TtsTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TtsTool.class);

    /** 默认音色 */
    private static final String DEFAULT_VOICE = "热情女声";

    private final TtsService ttsService;

    public TtsTool(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @Override
    public String getName() {
        return "text_to_speech";
    }

    @Override
    public String getDescription() {
        return "将文字合成为语音朗读出来。用户需提供要朗读的文字，可指定音色（可选，默认热情女声）。"
            + "可用音色：热情女声、温柔女声、甜美女声、活力女声、磁性男声、青年男声";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "string");
        text.put("description", "要朗读的文字内容");
        properties.put("text", text);

        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("type", "string");
        voice.put("description", "音色名称，默认为热情女声");
        voice.put("enum", List.of("热情女声", "温柔女声", "甜美女声", "活力女声", "磁性男声", "青年男声"));
        properties.put("voice", voice);

        params.put("properties", properties);
        params.put("required", List.of("text"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String text = arguments != null ? (String) arguments.get("text") : null;
        if (text == null || text.isBlank()) {
            return ToolResult.text("错误: 请提供要朗读的文字内容");
        }

        String voice = arguments != null ? (String) arguments.get("voice") : null;
        if (voice == null || voice.isBlank()) {
            voice = DEFAULT_VOICE;
        }

        String voiceId = ttsService.resolveVoice(voice);
        if (voiceId == null) {
            return ToolResult.text("不支持的音色: " + voice + "，可用音色：热情女声、温柔女声、甜美女声、活力女声、磁性男声、青年男声");
        }

        log.info("TtsTool 执行: text={}, voice={} -> voiceId={}", text, voice, voiceId);

        try {
            TtsService.TtsResult ttsResult = ttsService.synthesizeMp3(text, voiceId);
            log.info("语音合成成功: {} bytes", ttsResult.audioBytes().length);
            return ToolResult.audio(
                "语音已合成，音色：" + voice,
                ttsResult.audioBytes(),
                "tts.mp3"
            );
        } catch (Exception e) {
            log.error("语音合成失败", e);
            return ToolResult.text("语音合成失败: " + e.getMessage());
        }
    }
}
