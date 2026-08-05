package com.example.youkeda_project.service.assist;

import com.example.youkeda_project.config.ApiConfig;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

@Component
public class ChatTextClient {

    private final ApiConfig apiConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ChatTextClient(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String complete(String url, String apiKey, String model, String systemPrompt, String userContent, Duration timeout) throws Exception {
        return complete(url, apiKey, model, systemPrompt, userContent, timeout, null);
    }

    public String complete(String url, String apiKey, String model, String systemPrompt, String userContent, Duration timeout, Consumer<String> responseLogger) throws Exception {
        String body = "{\"model\":\"" + model + "\",\"messages\":["
            + "{\"role\":\"system\",\"content\":" + jsonEscape(systemPrompt) + "},"
            + "{\"role\":\"user\",\"content\":" + jsonEscape(userContent) + "}"
            + "]}";

        var hreq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(timeout)
                .build();

        HttpResponse<String> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofString());
        String respBody = hresp.body();
        if (responseLogger != null) responseLogger.accept(respBody);

        JsonNode root = objectMapper.readTree(respBody);
        JsonNode choice = root.path("choices").get(0);
        if (choice == null) return null;
        return choice.path("message").path("content").asText("");
    }

    public static String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
