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

    public String getWeatherApiKey() {
        return weatherApiKey;
    }

    public String getWeatherApiUrl() {
        return weatherApiUrl;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }
}