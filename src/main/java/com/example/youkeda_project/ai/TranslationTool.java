package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.TranslationService;
import com.example.youkeda_project.service.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TranslationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TranslationTool.class);

    private final TranslationService translationService;
    private final TtsService ttsService;

    public TranslationTool(TranslationService translationService, TtsService ttsService) {
        this.translationService = translationService;
        this.ttsService = ttsService;
    }

    @Override
    public String getName() {
        return "translate";
    }

    @Override
    public String getDescription() {
        return "[方言转换专用]用户想转换方言,讲方言,方言版等时,必须用此工具(传dialect参数),禁止用你自已的知识做方言转换!\n"
            + "还支持:通用翻译(传targetLang),文言文互译(mode=classical),计算机术语精准翻译(mode=tech)."
            + "所有翻译模式均自动朗读翻译结果."
            + "用法:text=要翻译的文本 targetLang=目标语种(en/ja/ko/fr/zh) mode=general/classical/dialect/tech dialect=方言名称";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "string");
        text.put("description", "要翻译或转换的文本");
        properties.put("text", text);

        Map<String, Object> targetLang = new LinkedHashMap<>();
        targetLang.put("type", "string");
        targetLang.put("description", "目标语种代码:en=英语, ja=日语, ko=韩语, fr=法语, zh=中文.通用翻译模式(general)必填,其他模式无需此参数.");
        properties.put("targetLang", targetLang);

        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("type", "string");
        mode.put("description", "翻译模式:general=通用翻译(默认), classical=文言文<->白话文, dialect=方言转换, tech=计算机专业术语精准翻译");
        properties.put("mode", mode);

        Map<String, Object> dialect = new LinkedHashMap<>();
        dialect.put("type", "string");
        dialect.put("description", "方言名称,仅 mode=dialect 时使用.支持:四川话,东北话,粤语,上海话,河南话,陕西话,湖南话,闽南语,客家话,天津话,山东话,武汉话,重庆话,云南话,贵州话等.不指定则自动判断.");
        properties.put("dialect", dialect);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null || arguments.get("text") == null) {
            return ToolResult.text("请提供要翻译的文本");
        }

        String text = (String) arguments.get("text");
        String targetLang = (String) arguments.get("targetLang");
        String mode = arguments.containsKey("mode") ? (String) arguments.get("mode") : "general";
        String dialect = (String) arguments.get("dialect");

        if (text.isBlank()) {
            return ToolResult.text("请输入要翻译的文本");
        }

        String result = translationService.translate(text, targetLang, mode, dialect);

        // 翻译后自动生成语音,让用户听到翻译结果
        try {
            TtsService.TtsResult ttsResult = ttsService.synthesizeMp3(result, ttsService.getDefaultVoice());
            log.info("翻译语音合成成功: {} bytes, {}s", ttsResult.audioBytes().length, ttsResult.durationSeconds());
            return ToolResult.audio(result, ttsResult.audioBytes(), "translation.mp3", ttsResult.durationSeconds());
        } catch (Exception e) {
            log.error("翻译语音合成失败,仅返回文字", e);
        }

        return ToolResult.text(result);
    }
}
