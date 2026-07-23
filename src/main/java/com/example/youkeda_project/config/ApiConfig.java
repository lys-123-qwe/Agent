package com.example.youkeda_project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 外部 API 配置，从 application.properties 读取各服务的 Key 和 URL。
 * <p>
 * 包含: 和风天气、DeepSeek AI、通义万相（生图）、通义千问 VL（图识）、Paraformer（语音识别）五个外部服务。
 */
@Configuration
public class ApiConfig {

    @Value("${amap.api.key:}")
    private String amapApiKey;

    @Value("${weather.api.key:}")
    private String weatherApiKey;

    @Value("${weather.api.url:https://devapi.qweather.com/v7/weather/now}")
    private String weatherApiUrl;

    @Value("${ai.api.key:}")
    private String aiApiKey;

    @Value("${ai.api.url:https://api.deepseek.com/chat/completions}")
    private String aiApiUrl;

    public String getAmapApiKey() {
        return amapApiKey;
    }

    public String getAmapGeoUrl() {
        return "https://restapi.amap.com/v3/geocode/geo";
    }

    public String getWeatherApiKey() {
        return weatherApiKey;
    }

    public String getWeatherApiUrl() {
        return weatherApiUrl;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }

    public String getAiApiUrl() {
        return aiApiUrl;
    }

    @Value("${image.api.key:}")
    private String imageApiKey;

    @Value("${image.api.url:https://dashscope.aliyuncs.com}")
    private String imageApiUrl;

    // 通义千问 VL 使用 dashscope 的兼容模式地址，与 OpenAI 格式通用
    @Value("${vision.api.url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String visionApiUrl;

    // 语音识别 Paraformer API 地址
    @Value("${voice.api.url:https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcriptions}")
    private String voiceApiUrl;

    // 语音识别模型：paraformer-v2 / paraformer-v1 / fun-asr-mtl 等
    @Value("${voice.api.model:paraformer-v2}")
    private String voiceApiModel;

    // 语音合成（TTS）非实时 HTTP API 地址
    private static final String TTS_API_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer";

    // 语音合成模型
    @Value("${tts.api.model:cosyvoice-v3-flash}")
    private String ttsApiModel;

    public String getDeepSeekApiKey() {
        return aiApiKey;
    }

    public String getDeepSeekApiUrl() {
        return aiApiUrl;
    }

    public String getImageApiKey() {
        return imageApiKey;
    }

    public String getImageApiUrl() {
        return imageApiUrl;
    }

    // 通义千问 VL 复用 image.api.key 作为鉴权
    public String getVisionApiKey() {
        return imageApiKey;
    }

    public String getVisionApiUrl() {
        return visionApiUrl;
    }

    // 语音识别复用 image.api.key（阿里云账号共用）
    public String getVoiceApiKey() {
        return imageApiKey;
    }

    public String getVoiceApiUrl() {
        return voiceApiUrl;
    }

    public String getVoiceApiModel() {
        return voiceApiModel;
    }

    public String getTtsApiUrl() {
        return TTS_API_URL;
    }

    public String getTtsApiModel() {
        return ttsApiModel;
    }

    @Value("${bocha.api.key:}")
    private String bochaApiKey;


    @Value("${exchange.api.key:}")
    private String exchangeApiKey;

    @Value("${exchange.api.url:https://v6.exchangerate-api.com/v6}")
    private String exchangeApiUrl;

    public String getExchangeApiKey() {
        return exchangeApiKey;
    }

    public String getExchangeApiUrl() {
        return exchangeApiUrl;
    }

    @Value("${constellation.query.api.key:}")
    private String constellationQueryKey;

    @Value("${constellation.query.api.url:http://web.juhe.cn:8080/constellation/getAll}")
    private String constellationQueryUrl;

    @Value("${constellation.horoscope.api.key:}")
    private String constellationHoroscopeKey;

    @Value("${constellation.horoscope.api.url:https://web.juhe.cn/constellation/getAll}")
    private String constellationHoroscopeUrl;

    @Value("${phone.api.key:}")
    private String phoneApiKey;

    @Value("${phone.api.url:http://op.juhe.cn/phone/get}")
    private String phoneApiUrl;

    public String getConstellationQueryKey() {
        return constellationQueryKey;
    }

    public String getConstellationQueryUrl() {
        return constellationQueryUrl;
    }

    public String getConstellationHoroscopeKey() {
        return constellationHoroscopeKey;
    }

    public String getConstellationHoroscopeUrl() {
        return constellationHoroscopeUrl;
    }

    public String getPhoneApiKey() {
        return phoneApiKey;
    }

    public String getPhoneApiUrl() {
        return phoneApiUrl;
    }


    public String getBochaApiKey() {
        return bochaApiKey;
    }
}