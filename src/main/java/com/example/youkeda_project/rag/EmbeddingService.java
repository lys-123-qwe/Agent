package com.example.youkeda_project.rag;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final String EMBEDDING_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    private static final int DIMENSION = 1024;

    private final RestClient restClient;
    private final ApiConfig apiConfig;

    public EmbeddingService(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
        this.restClient = RestClient.builder().baseUrl(EMBEDDING_URL).build();
    }

    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        try {
            Map<String, Object> request = Map.of(
                "model", "text-embedding-v3",
                "input", Map.of("texts", List.of(text)),
                "parameters", Map.of("dimension", DIMENSION)
            );
            Map<String, Object> response = restClient.post()
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiConfig.getImageApiKey())
                .body(request)
                .retrieve()
                .body(Map.class);
            List<Map<String, Object>> embeddings = (List<Map<String, Object>>)
                ((Map<String, Object>) response.get("output")).get("embeddings");
            List<Double> vec = (List<Double>) embeddings.get(0).get("embedding");
            float[] result = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) result[i] = vec.get(i).floatValue();
            return result;
        } catch (Exception e) {
            log.warn("Embedding API 调用失败: {}", e.getMessage());
            return new float[DIMENSION];
        }
    }
}
