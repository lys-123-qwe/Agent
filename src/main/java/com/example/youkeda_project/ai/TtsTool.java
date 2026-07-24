package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return "把文字朗读出来（语音合成）。用户说朗读/读一下/念出来/语音时直接用此工具。"
            + "如果用户提到音色名（龙安洋/龙安欢/龙呼呼/龙书/Bella），一并传入 voice 参数；"
            + "如果用户没指定音色，不传 voice 参数，工具会自动用默认音色。"
            + "不要自己伪造语音链接！";
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
        voice.put("description", "音色（可选，不传则用默认音色）。可用值：龙安洋(阳光男声)、龙安欢(元气女声)、龙呼呼(可爱童声)、龙书(沉稳青年男)、Bella(干练女声)");
        voice.put("enum", List.of("龙安洋", "龙安欢", "龙呼呼", "龙书", "Bella"));
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
            voice = "龙安洋";
        }

        String voiceId = ttsService.resolveVoice(voice);
        if (voiceId == null) {
            voiceId = ttsService.getDefaultVoice();
        }

        log.info("TtsTool 执行: text={}, voice={} -> voiceId={}", text, voice, voiceId);

        try {
            TtsService.TtsResult ttsResult = ttsService.synthesizeMp3(text, voiceId);
            log.info("语音合成成功: {} bytes, {}s", ttsResult.audioBytes().length, ttsResult.durationSeconds());
            return ToolResult.audio(
                "语音已合成，音色：" + voice,
                ttsResult.audioBytes(),
                "tts.mp3",
                ttsResult.durationSeconds()
            );
        } catch (Exception e) {
            log.error("语音合成失败", e);
            return ToolResult.text("语音合成失败: " + e.getMessage());
        }
    }
}
