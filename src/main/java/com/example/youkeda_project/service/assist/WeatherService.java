package com.example.youkeda_project.service.assist;

import com.example.youkeda_project.config.ApiConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 和风天气查询服务.
 * <p>
 * 通过高德地理编码 API 将城市名转换为经纬度坐标,
 * 再用坐标调用和风天气实时天气 API(devapi.qweather.com),
 * 返回格式化天气信息字符串.
 * <p>
 * 支持任意中国城市名称(中文).
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final RestClient restClient;
    private final ApiConfig apiConfig;
    private final ObjectMapper objectMapper;

    public WeatherService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /**
     * 查询指定城市的实时天气.
     * <p>
     * 流程:城市名 → 高德地理编码 → 经纬度坐标 → 和风天气 API → 格式化天气信息.
     */
    public String queryWeather(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            log.warn("天气查询失败: 城市名为空");
            return "错误: 请输入城市名,例如: weather 北京";
        }

        String cityNameTrimmed = cityName.trim();
        log.info("查询天气: 城市={}", cityNameTrimmed);

        try {
            // 第1步:高德地理编码,城市名 → 坐标
            String location = geocode(cityNameTrimmed);
            if (location == null) {
                return "错误: 未找到城市 '" + cityNameTrimmed
                        + "' 的地理坐标,请检查城市名是否正确";
            }
            log.info("地理编码结果: {} -> {}", cityNameTrimmed, location);

            // 第2步:用坐标查询和风天气
            String url = apiConfig.getWeatherApiUrl() + "?location=" + location
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
     * 调用高德地理编码 API,将城市名转换为经纬度坐标.
     *
     * @param cityName 城市名称(中文)
     * @return "lon,lat" 格式的坐标字符串,无结果时返回 null
     */
    private String geocode(String cityName) {
        try {
            String url = apiConfig.getAmapGeoUrl() + "?key=" + apiConfig.getAmapApiKey()
                    + "&address=" + cityName
                    + "&output=JSON";

            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            log.info("高德地理编码响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            String status = root.path("status").asText();

            if (!"1".equals(status)) {
                log.warn("高德地理编码失败: status={}, info={}",
                        status, root.path("info").asText());
                return null;
            }

            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) {
                log.warn("高德地理编码无结果: city={}", cityName);
                return null;
            }

            String location = geocodes.get(0).path("location").asText();
            if (location.isEmpty()) {
                log.warn("高德地理编码结果中无坐标: city={}", cityName);
                return null;
            }

            return location;

        } catch (Exception e) {
            log.error("高德地理编码异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析和风天气 API 的 JSON 响应.
     * 提取天气状况,温度,体感温度,湿度,风向,风力等级.
     */
    private String parseWeatherResponse(String json, String cityName) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String code = root.path("code").asText();

            if (!"200".equals(code)) {
                log.warn("天气API返回错误: code={}", code);
                return "错误: 天气API返回异常,状态码: " + code;
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
