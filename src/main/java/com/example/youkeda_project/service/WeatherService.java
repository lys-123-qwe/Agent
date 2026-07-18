package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 和风天气查询服务。
 * <p>
 * 预置 10 个中国主要城市 ID，支持中英文城市名。
 * 调用和风天气实时天气 API（devapi.qweather.com），
 * 返回格式化天气信息字符串。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final RestClient restClient;
    private final ApiConfig apiConfig;
    private final ObjectMapper objectMapper;

    // 预置 10 个城市的中英文名到和风城市 ID 的映射
    private static final Map<String, String> CITY_MAP;

    static {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("北京", "101010100");
        map.put("beijing", "101010100");
        map.put("上海", "101020100");
        map.put("shanghai", "101020100");
        map.put("广州", "101280101");
        map.put("guangzhou", "101280101");
        map.put("深圳", "101280601");
        map.put("shenzhen", "101280601");
        map.put("杭州", "101210101");
        map.put("hangzhou", "101210101");
        map.put("成都", "101270101");
        map.put("chengdu", "101270101");
        map.put("武汉", "101200101");
        map.put("wuhan", "101200101");
        map.put("南京", "101190101");
        map.put("nanjing", "101190101");
        map.put("西安", "101110101");
        map.put("xian", "101110101");
        map.put("重庆", "101040100");
        map.put("chongqing", "101040100");
        CITY_MAP = Map.copyOf(map);
    }

    public WeatherService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /**
     * 查询指定城市的实时天气。
     * 先在预置 CITY_MAP 中查找城市 ID，找不到返回错误信息。
     * 查询结果解析后返回格式化的天气字符串。
     */
    public String queryWeather(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            log.warn("天气查询失败: 城市名为空");
            return "错误: 请输入城市名，例如: weather 北京";
        }

        String cityNameTrimmed = cityName.trim();
        // 不区分大小写匹配
        String cityId = CITY_MAP.get(cityNameTrimmed.toLowerCase());

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

    /**
     * 解析和风天气 API 的 JSON 响应。
     * 提取天气状况、温度、体感温度、湿度、风向、风力等级。
     */
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