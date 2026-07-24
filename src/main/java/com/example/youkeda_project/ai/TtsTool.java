package com.example.youkeda_project.ai;

import com.example.youkeda_project.config.ApiConfig;
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
 * voice 参数必填，调用前必须询问用户选择哪种音色，用户明确回复后再传参调用。
 * 不要擅自选择音色，必须等用户指定后再生成。
 */
@Component
public class TtsTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TtsTool.class);

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
        return "将文字合成为语音朗读出来。"
            + "【重要】调用此工具前，必须先询问用户选择哪种音色（用户可回复数字或音色名），用户明确回复后再调用。不要擅自选择音色。"
            + "可用音色：\n1. 龙安洋(阳光男声)\n2. 龙安欢(元气女声)\n3. 龙呼呼(可爱童声)\n4. 龙书(沉稳青年男)\n5. Bella(干练女声)";
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
        voice.put("description", "用户选择的音色名称（可输入音色名或对应数字）。必须等用户明确回复后再传此参数");
        voice.put("enum", List.of("龙安洋", "龙安欢", "龙呼呼", "龙书", "Bella"));
        properties.put("voice", voice);

        params.put("properties", properties);
        params.put("required", List.of("text", "voice"));
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
            return ToolResult.text("请先让用户选择音色再调用此工具。可用音色：\n1. 龙安洋(阳光男声)\n2. 龙安欢(元气女声)\n3. 龙呼呼(可爱童声)\n4. 龙书(沉稳青年男)\n5. Bella(干练女声)");
        }

        String voiceId = ttsService.resolveVoice(voice);
        if (voiceId == null) {
            return ToolResult.text("不支持的音色: " + voice + "，可用音色：\n1. 龙安洋(阳光男声)\n2. 龙安欢(元气女声)\n3. 龙呼呼(可爱童声)\n4. 龙书(沉稳青年男)\n5. Bella(干练女声)");
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
