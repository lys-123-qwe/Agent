package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final RestClient restClient;
    private final ApiConfig apiConfig;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> CITY_MAP = Map.of(
            "北京", "101010100",
            "上海", "101020100",
            "广州", "101280101",
            "深圳", "101280601",
            "杭州", "101210101",
            "成都", "101270101",
            "武汉", "101200101",
            "南京", "101190101",
            "西安", "101110101",
            "重庆", "101040100"
    );

    public WeatherService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String queryWeather(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            log.warn("天气查询失败: 城市名为空");
            return "错误: 请输入城市名，例如: weather 北京";
        }

        String cityNameTrimmed = cityName.trim();
        String cityId = CITY_MAP.get(cityNameTrimmed);

        if (cityId == null) {
            log.warn("天气查询失败: 不支持的城市 '{}'", cityNameTrimmed);
            return "错误: 暂不支持查询城市 '" + cityNameTrimmed
                    + "'\n支持的城市: " + String.join("、", CITY_MAP.keySet());
        }

        log.info("查询天气: 城市={}, 城市ID={}", cityNameTrimmed, cityId);

        try {
            String url = apiConfig.getWeatherApiUrl() + "?location=" + cityId
                    + "&key=" + apiConfig.getWeatherApiKey();

            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            log.info("天气API响应: {}", response);
            return parseWeatherResponse(response, cityNameTrimmed);

        } catch (Exception e) {
            log.error("天气查询异常: {}", e.getMessage(), e);
            return "错误: 天气查询失败 - " + e.getMessage();
        }
    }

    private String parseWeatherResponse(String json, String cityName) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String code = root.path("code").asText();

            if (!"200".equals(code)) {
                log.warn("天气API返回错误: code={}", code);
                return "错误: 天气API返回异常，状态码: " + code;
            }

            JsonNode now = root.path("now");
            String weather = now.path("text").asText();
            String temp = now.path("temp").asText();
            String feelsLike = now.path("feelsLike").asText();
            String humidity = now.path("humidity").asText();
            String windDir = now.path("windDir").asText();
            String windScale = now.path("windScale").asText();

            return String.format("""
                    %s天气:
                      天气: %s
                      温度: %s℃ (体感 %s℃)
                      湿度: %s%%
                      风向: %s %s级""",
                    cityName, weather, temp, feelsLike, humidity, windDir, windScale);

        } catch (Exception e) {
            log.error("解析天气响应失败: {}", e.getMessage(), e);
            return "错误: 解析天气数据失败 - " + e.getMessage();
        }
    }
}