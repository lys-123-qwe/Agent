package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本向量化服务，接入阿里云 Dashscope text-embedding-v3。
 * <p>
 * 将文本转为 1024 维归一化向量，用于知识库语义检索。
 * 支持单条和批量调用，批量模式可大幅减少网络开销。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int DIMENSION = 1024;

    private final ApiConfig apiConfig;
    private final HttpClient httpClient;

    public EmbeddingService(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 单条文本向量化。
     */
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    /**
     * 批量文本向量化（一次 HTTP 调用处理多条，大幅降低延迟）。
     *
     * @param texts 待向量化的文本列表
     * @return 与输入顺序对应的向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        List<float[]> results = new ArrayList<>();
        // Dashscope text-embedding-v3 单次最多 10 条
        for (int offset = 0; offset < texts.size(); offset += 10) {
            int end = Math.min(offset + 10, texts.size());
            List<String> batch = texts.subList(offset, end);
            results.addAll(doEmbedBatch(batch));
        }
        return results;
    }

    private List<float[]> doEmbedBatch(List<String> texts) {
        String apiKey = apiConfig.getEmbeddingApiKey();
        String apiUrl = apiConfig.getEmbeddingApiUrl();
        String model = apiConfig.getEmbeddingModel();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Embedding API Key 未配置，回退到 BM25");
            return texts.stream().map(t -> new float[0]).toList();
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", Map.of("texts", texts)
            );

            String body = mapper.writeValueAsString(requestBody);
            log.debug("Embedding 请求: {} 条文本, {} chars", texts.size(), body.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("Embedding API 返回非 200: status={}, body={}",
                        response.statusCode(),
                        response.body().substring(0, Math.min(500, response.body().length())));
                return texts.stream().map(t -> new float[0]).toList();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode embeddings = root.path("output").path("embeddings");
            if (!embeddings.isArray()) {
                log.error("Embedding API 响应格式异常: {}", response.body().substring(0, 200));
                return texts.stream().map(t -> new float[0]).toList();
            }

            List<float[]> result = new ArrayList<>();
            for (JsonNode emb : embeddings) {
                JsonNode vecNode = emb.path("embedding");
                if (!vecNode.isArray()) {
                    result.add(new float[0]);
                    continue;
                }
                float[] vec = new float[vecNode.size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = vecNode.get(i).floatValue();
                }
                result.add(vec);
            }

            log.debug("Embedding 完成: {} 条 → {} 维向量", result.size(), result.isEmpty() ? 0 : result.get(0).length);
            return result;

        } catch (Exception e) {
            log.error("Embedding API 调用失败: {}", e.getMessage());
            // 返回空向量，调用方会自动回退 BM25
            return texts.stream().map(t -> new float[0]).toList();
        }
    }

    // ==================== 向量工具方法 ====================

    /** 向量序列化为字节（用于 SQLite BLOB 存储） */
    public static byte[] toBytes(float[] vec) {
        if (vec == null || vec.length == 0) return new byte[0];
        ByteBuffer buf = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (float v : vec) buf.putFloat(v);
        return buf.array();
    }

    /** 从字节反序列化向量 */
    public static float[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new float[0];
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] vec = new float[bytes.length / 4];
        for (int i = 0; i < vec.length; i++) vec[i] = buf.getFloat();
        return vec;
    }

    /** 余弦相似度（Dashscope 返回归一化向量，内积即余弦） */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0) return 0;
        int len = Math.min(a.length, b.length);
        double dot = 0;
        for (int i = 0; i < len; i++) dot += (double) a[i] * b[i];
        return dot; // 归一化向量省略分母
    }

    public static int getDimension() { return DIMENSION; }
}
