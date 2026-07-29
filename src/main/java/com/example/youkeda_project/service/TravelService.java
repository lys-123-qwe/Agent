package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public TravelService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String query(String origin, String destination) {
        if (apiConfig.getAmapApiKey() == null || apiConfig.getAmapApiKey().isBlank()) {
            return "错误: 未配置高德地图 API Key";
        }

        // 地理编码:地名 → 坐标
        String originLoc = geocode(origin);
        if (originLoc == null) return "无法定位:" + origin;
        String destLoc = geocode(destination);
        if (destLoc == null) return "无法定位:" + destination;

        log.info("起点: {} → {},终点: {} → {}", origin, originLoc, destination, destLoc);

        StringBuilder sb = new StringBuilder();
        sb.append("🗺️ **").append(origin).append(" → ").append(destination).append("**\n\n");

        // 驾车
        appendDriving(sb, originLoc, destLoc);
        // 公交(含地铁)
        appendTransit(sb, originLoc, destLoc, origin, destination);
        // 骑行
        appendBicycling(sb, originLoc, destLoc);
        // 步行
        appendWalking(sb, originLoc, destLoc);

        return sb.toString().trim();
    }

    private void appendDriving(StringBuilder sb, String origin, String dest) {
        try {
            String url = "https://restapi.amap.com/v3/direction/driving?key=" + apiConfig.getAmapApiKey()
                + "&origin=" + origin + "&destination=" + dest
                + "&strategy=0&output=JSON&extensions=all";
            JsonNode root = callApi(url);
            if (root == null) return;

            JsonNode route = root.path("route");
            JsonNode paths = route.path("paths");
            if (!paths.isArray() || paths.isEmpty()) return;

            JsonNode best = paths.get(0);
            String distance = formatDistance(best.path("distance").asLong());
            String duration = formatDuration(best.path("duration").asLong());
            String tolls = best.path("tolls").asText();
            String taxiCost = best.path("taxi_cost").asText();

            sb.append("🚗 **驾车**");
            if (!"0".equals(taxiCost) && !taxiCost.isEmpty()) {
                sb.append("(打车约 ").append(taxiCost).append(" 元)");
            }
            sb.append("\n距离:").append(distance).append(" | 耗时:").append(duration);
            if (!"0".equals(tolls) && !tolls.isEmpty()) {
                sb.append(" | 过路费:").append(tolls).append(" 元");
            }
            sb.append("\n");

            // 途经道路
            JsonNode steps = best.path("steps");
            if (steps.isArray()) {
                sb.append("  途经:");
                int count = 0;
                for (JsonNode step : steps) {
                    if (count++ >= 5) { sb.append("..."); break; }
                    String road = step.path("road").asText();
                    String instruction = step.path("instruction").asText();
                    if (!road.isBlank()) sb.append(road).append(" → ");
                    else if (!instruction.isBlank()) sb.append(instruction).append(" → ");
                }
                if (sb.charAt(sb.length()-1) == ' ' && sb.charAt(sb.length()-2) == '→') {
                    sb.setLength(sb.length() - 2);
                }
            }
            sb.append("\n\n");
        } catch (Exception e) {
            log.warn("驾车查询失败", e);
        }
    }

    private void appendTransit(StringBuilder sb, String origin, String dest, String originName, String destName) {
        try {
            String encodedOrigin = URLEncoder.encode(originName, StandardCharsets.UTF_8.name());
            String encodedDest = URLEncoder.encode(destName, StandardCharsets.UTF_8.name());
            String url = "https://restapi.amap.com/v3/direction/transit/integrated?key=" + apiConfig.getAmapApiKey()
                + "&origin=" + origin + "&destination=" + dest
                + "&city=" + encodedOrigin + "&cityd=" + encodedDest
                + "&strategy=0&output=JSON&extensions=all";
            JsonNode root = callApi(url);
            if (root == null) return;

            JsonNode route = root.path("route");
            JsonNode transits = route.path("transits");
            if (!transits.isArray() || transits.isEmpty()) return;

            JsonNode best = transits.get(0);
            String cost = best.path("cost").path("total_text").asText();
            String duration = formatDuration(best.path("duration").asLong());
            String walkingDist = formatDistance(best.path("walking_distance").asLong());

            sb.append("🚇 **公交/地铁**");
            if (!cost.isBlank()) sb.append("(票价 ").append(cost).append(" 元)");
            sb.append("\n耗时:").append(duration).append(" | 步行:").append(walkingDist).append("\n");

            // 途经站点
            JsonNode segments = best.path("segments");
            if (segments.isArray()) {
                sb.append("  路线:");
                for (JsonNode seg : segments) {
                JsonNode bus = seg.path("bus");
                if (!bus.isNull() && bus.has("buslines")) {
                    JsonNode buslines = bus.path("buslines");
                    if (buslines.isArray() && !buslines.isEmpty()) {
                        JsonNode line = buslines.get(0);
                        String lineName = line.path("name").asText();
                        String departStop = line.path("departure_stop").path("name").asText();
                        String arrivalStop = line.path("arrival_stop").path("name").asText();
                        int stopCount = line.path("via_num").asInt();
                        String type = line.path("type").asText();
                        String prefix = "🚌";
                        if (type.contains("地铁")) prefix = "🚇";
                        sb.append(prefix).append(" ").append(lineName).append("\n    ");
                        sb.append(departStop).append(" → ");
                        // 途经站
                        JsonNode viaStops = line.path("via_stops");
                        if (viaStops.isArray()) {
                            int shown = 0;
                            for (JsonNode stop : viaStops) {
                                if (shown++ >= 10) { sb.append("..."); break; }
                                sb.append(stop.path("name").asText()).append(" → ");
                            }
                        }
                        sb.append(arrivalStop);
                        sb.append("(").append(stopCount).append("站)");
                        sb.append("\n");
                    }
                }
                    JsonNode railway = seg.path("railway");
                    if (!railway.isNull() && railway.has("name")) {
                        sb.append("🚄 ").append(railway.path("name").asText())
                            .append(" → ");
                    }
                    JsonNode walking = seg.path("walking");
                    if (!walking.isNull() && walking.has("distance")) {
                        long wDist = walking.path("distance").asLong();
                        if (wDist > 100) {
                            sb.append("    🚶步行").append(formatDistance(wDist)).append("\n");
                        }
                    }
                }
            }
            sb.append("\n\n");
        } catch (Exception e) {
            log.warn("公交查询失败", e);
        }
    }

    private void appendBicycling(StringBuilder sb, String origin, String dest) {
        try {
            String url = "https://restapi.amap.com/v3/direction/bicycling?key=" + apiConfig.getAmapApiKey()
                + "&origin=" + origin + "&destination=" + dest + "&output=JSON";
            JsonNode root = callApi(url);
            if (root == null) return;

            JsonNode route = root.path("route");
            JsonNode paths = route.path("paths");
            if (!paths.isArray() || paths.isEmpty()) return;

            JsonNode best = paths.get(0);
            String distance = formatDistance(best.path("distance").asLong());
            String duration = formatDuration(best.path("duration").asLong());

            sb.append("🚲 **骑行**\n距离:").append(distance).append(" | 耗时:").append(duration).append("\n\n");
        } catch (Exception e) {
            log.warn("骑行查询失败", e);
        }
    }

    private void appendWalking(StringBuilder sb, String origin, String dest) {
        try {
            String url = "https://restapi.amap.com/v3/direction/walking?key=" + apiConfig.getAmapApiKey()
                + "&origin=" + origin + "&destination=" + dest + "&output=JSON";
            JsonNode root = callApi(url);
            if (root == null) return;

            JsonNode route = root.path("route");
            JsonNode paths = route.path("paths");
            if (!paths.isArray() || paths.isEmpty()) return;

            JsonNode best = paths.get(0);
            String distance = formatDistance(best.path("distance").asLong());
            String duration = formatDuration(best.path("duration").asLong());

            sb.append("🚶 **步行**\n距离:").append(distance).append(" | 耗时:").append(duration).append("\n");
        } catch (Exception e) {
            log.warn("步行查询失败", e);
        }
    }

    private JsonNode callApi(String url) {
        try {
            log.info("高德API请求: {}", url.replace(apiConfig.getAmapApiKey(), "***"));
            String response = restClient.get().uri(url).retrieve().body(String.class);
            log.info("高德API响应: {}", response);
            JsonNode root = objectMapper.readTree(response);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("高德API失败: info={}", root.path("info").asText());
                return null;
            }
            return root;
        } catch (Exception e) {
            log.warn("高德API调用异常", e);
            return null;
        }
    }

    private String geocode(String address) {
        try {
            String url = apiConfig.getAmapGeoUrl() + "?key=" + apiConfig.getAmapApiKey()
                + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                + "&output=JSON";
            log.info("地理编码请求: address={}", address);
            String response = restClient.get()
                .uri(java.net.URI.create(url))
                .retrieve()
                .body(String.class);
            log.info("地理编码响应: {}", response);
            JsonNode root = objectMapper.readTree(response);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("地理编码失败: info={}", root.path("info").asText());
                return null;
            }
            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) return null;
            return geocodes.get(0).path("location").asText();
        } catch (Exception e) {
            log.warn("地理编码异常: {}", address, e);
            return null;
        }
    }

    private String formatDistance(long meters) {
        if (meters >= 1000) return String.format("%.1f km", meters / 1000.0);
        return meters + " m";
    }

    private String formatDuration(long seconds) {
        if (seconds >= 3600) return String.format("%d 小时 %d 分钟", seconds / 3600, (seconds % 3600) / 60);
        return (seconds / 60) + " 分钟";
    }
}
