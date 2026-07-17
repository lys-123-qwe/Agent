package com.example.youkeda_project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiConfig {

    @Value("${weather.api.key:}")
    private String weatherApiKey;

    @Value("${weather.api.url:https://devapi.qweather.com/v7/weather/now}")
    private String weatherApiUrl;

    @Value("${ai.api.key:}")
    private String aiApiKey;

    @Value("${ai.api.url:https://api.deepseek.com/chat/completions}")
    private String aiApiUrl;

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
}