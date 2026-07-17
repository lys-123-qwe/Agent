package com.example.youkeda_project.client;

import com.example.youkeda_project.protocol.CDNMedia;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.ImageItem;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.QRCodeResponse;
import com.example.youkeda_project.protocol.SendMessageRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class IlinkProtocolClient {

    private static final Logger log = LoggerFactory.getLogger(IlinkProtocolClient.class);
    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String CDN_BASE = "https://novac2c.cdn.weixin.qq.com/c2c";
    private static final int LONG_POLL_TIMEOUT_MS = 35000;

    private final ObjectMapper objectMapper;

    private RestClient restClient;
    private String botToken;
    private String baseUrl = DEFAULT_BASE_URL;

    public IlinkProtocolClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = buildRestClient(DEFAULT_BASE_URL);
    }

    public void setCredentials(String botToken, String baseUrl) {
        this.botToken = botToken;
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = baseUrl;
            this.restClient = buildRestClient(baseUrl);
            log.info("API地址已更新为: {}", baseUrl);
        }
    }

    public String getBotToken() {
        return botToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public QRCodeResponse getQRCode() {
        String json = restClient.get()
                .uri("/ilink/bot/get_bot_qrcode?bot_type=3")
                .retrieve()
                .body(String.class);
        return parseJson(json, QRCodeResponse.class);
    }

    public LoginResult pollQRStatus(String qrcode) {
        String json = restClient.get()
                .uri("/ilink/bot/get_qrcode_status?qrcode=" + qrcode)
                .retrieve()
                .body(String.class);
        return parseJson(json, LoginResult.class);
    }

    public GetUpdatesResponse getUpdates(String cursor) {
        String body = "{\"get_updates_buf\":\"" + (cursor != null ? cursor : "") + "\"}";
        String json = postWithAuth("/ilink/bot/getupdates", body, LONG_POLL_TIMEOUT_MS);
        return parseJson(json, GetUpdatesResponse.class);
    }

    public String sendMessage(String toUserId, String contextToken, List<MessageItem> items) {
        SendMessageRequest request = SendMessageRequest.items(toUserId, contextToken, items);
        try {
            String body = objectMapper.writeValueAsString(request);
            log.info("发送消息请求: {}", body);
            String response = postWithAuth("/ilink/bot/sendmessage", body, 15000);
            log.info("发送消息响应: {}", response);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("发送消息失败", e);
        }
    }

    public byte[] downloadAndDecryptImage(MessageItem item) {
        ImageItem imageItem = item.imageItem();
        if (imageItem == null || imageItem.media() == null) {
            throw new RuntimeException("消息中没有图片");
        }

        String url = null;
        if (imageItem.media().fullUrl() != null && !imageItem.media().fullUrl().isBlank()) {
            url = imageItem.media().fullUrl();
        } else if (imageItem.media().encryptQueryParam() != null && !imageItem.media().encryptQueryParam().isBlank()) {
            url = CDN_BASE + "/download?encrypted_query_param="
                + URLEncoder.encode(imageItem.media().encryptQueryParam(), StandardCharsets.UTF_8);
        }
        if (url == null) throw new RuntimeException("图片无下载URL");

        String aesKeyStr = imageItem.aeskey();
        if (aesKeyStr == null && imageItem.media().aesKey() != null) {
            aesKeyStr = imageItem.media().aesKey();
        }

        log.info("下载图片CDN: {}", url);
        byte[] encryptedBytes = RestClient.create().get()
            .uri(URI.create(url))
            .retrieve()
            .body(byte[].class);
        log.info("CDN下载完成: {} bytes", encryptedBytes != null ? encryptedBytes.length : 0);

        if (aesKeyStr == null) {
            log.warn("图片无AES key，返回原始密文");
            return encryptedBytes;
        }

        byte[] key = deriveAesKey(aesKeyStr);
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] decrypted = cipher.doFinal(encryptedBytes);
            int pad = decrypted[decrypted.length - 1];
            if (pad > 0 && pad <= 16) {
                return Arrays.copyOf(decrypted, decrypted.length - pad);
            }
            return decrypted;
        } catch (Exception e) {
            throw new RuntimeException("图片AES解密失败", e);
        }
    }

    private byte[] deriveAesKey(String aesKeyStr) {
        if (aesKeyStr == null) throw new IllegalArgumentException("AES key为空");

        if (aesKeyStr.matches("^[0-9a-fA-F]{32}$")) {
            return HexFormat.of().parseHex(aesKeyStr);
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(aesKeyStr);
        } catch (Exception e) {
            throw new RuntimeException("无法解析AES key: " + aesKeyStr);
        }

        if (decoded.length == 32) {
            String asHex = new String(decoded, StandardCharsets.UTF_8);
            if (asHex.matches("^[0-9a-fA-F]{32}$")) {
                return HexFormat.of().parseHex(asHex);
            }
        }

        if (decoded.length == 16) {
            return decoded;
        }

        throw new RuntimeException("无法解析AES key，长度不匹配: " + aesKeyStr);
    }

    public void uploadAndSendImage(String toUserId, String contextToken, byte[] imageBytes) {
        try {
            byte[] aesKeyBytes = new byte[16];
            ThreadLocalRandom.current().nextBytes(aesKeyBytes);
            String aesKeyHex = HexFormat.of().formatHex(aesKeyBytes);

            String fileKey = generateFileKey();

            byte[] md5Bytes = MessageDigest.getInstance("MD5").digest(imageBytes);
            String md5Hex = HexFormat.of().formatHex(md5Bytes);

            int rawSize = imageBytes.length;
            int paddedSize = (rawSize / 16 + 1) * 16;

            Map<String, Object> uploadReq = Map.of(
                "filekey", fileKey,
                "media_type", 1,
                "to_user_id", toUserId,
                "rawsize", rawSize,
                "rawfilemd5", md5Hex,
                "filesize", paddedSize,
                "no_need_thumb", true,
                "aeskey", aesKeyHex,
                "base_info", Map.of("channel_version", "1.0.2")
            );

            String uploadReqJson = objectMapper.writeValueAsString(uploadReq);
            log.info("getUploadUrl请求: {}", uploadReqJson);
            String uploadRespJson = postWithAuth("/ilink/bot/getuploadurl", uploadReqJson, 15000);
            log.info("getUploadUrl响应: {}", uploadRespJson);

            JsonNode uploadResp = objectMapper.readTree(uploadRespJson);
            JsonNode baseResp = uploadResp.path("base_resp");
            if (baseResp.has("ret") && baseResp.get("ret").asInt() != 0) {
                throw new RuntimeException("获取上传URL失败: ret=" + baseResp.get("ret")
                    + ", errmsg=" + baseResp.path("errmsg").asText());
            }

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"));
            byte[] encryptedBytes = cipher.doFinal(imageBytes);

            String uploadFullUrl = uploadResp.has("upload_full_url")
                ? uploadResp.get("upload_full_url").asText() : null;
            String uploadParam = uploadResp.has("upload_param")
                ? uploadResp.get("upload_param").asText() : null;

            String encryptQueryParam = uploadToCdn(encryptedBytes, uploadFullUrl, uploadParam, fileKey);

            String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKeyHex.getBytes(StandardCharsets.UTF_8));
            CDNMedia media = new CDNMedia(encryptQueryParam, aesKeyBase64);

            SendMessageRequest request = SendMessageRequest.cdnImage(toUserId, contextToken, media, encryptedBytes.length);
            String msgBody = objectMapper.writeValueAsString(request);
            log.info("发送图片CDN消息: {}", msgBody);
            String response = postWithAuth("/ilink/bot/sendmessage", msgBody, 15000);
            log.info("发送图片响应: {}", response);

        } catch (Exception e) {
            throw new RuntimeException("图片上传发送失败", e);
        }
    }

    private String uploadToCdn(byte[] encryptedBytes, String uploadFullUrl, String uploadParam, String fileKey) {
        String cdnUrl;
        if (uploadFullUrl != null && !uploadFullUrl.isBlank()) {
            cdnUrl = uploadFullUrl;
        } else if (uploadParam != null && !uploadParam.isBlank()) {
            String encodedParam = URLEncoder.encode(uploadParam, StandardCharsets.UTF_8);
            cdnUrl = CDN_BASE + "/upload?encrypted_query_param=" + encodedParam + "&filekey=" + fileKey;
        } else {
            throw new RuntimeException("getUploadUrl未返回upload_full_url或upload_param");
        }

        log.info("上传图片到CDN: {}", cdnUrl);
        ResponseEntity<byte[]> resp = RestClient.create().post()
            .uri(cdnUrl)
            .header("Content-Type", "application/octet-stream")
            .body(encryptedBytes)
            .retrieve()
            .toEntity(byte[].class);

        String encryptQueryParam = resp.getHeaders().getFirst("x-encrypted-param");
        if (encryptQueryParam == null || encryptQueryParam.isBlank()) {
            throw new RuntimeException("CDN上传未返回x-encrypted-param header");
        }
        return encryptQueryParam;
    }

    private String generateFileKey() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String postWithAuth(String path, String body, int timeoutMs) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("未登录，缺少 bot_token");
        }
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] responseBytes = restClient.post()
                .uri(path)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("AuthorizationType", "ilink_bot_token")
                .header("Authorization", "Bearer " + botToken)
                .header("X-WECHAT-UIN", generateWechatUin())
                .body(bodyBytes)
                .retrieve()
                .body(byte[].class);
        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    private String generateWechatUin() {
        int random = ThreadLocalRandom.current().nextInt();
        String decimal = Integer.toUnsignedString(random);
        return Base64.getEncoder().encodeToString(decimal.getBytes(StandardCharsets.UTF_8));
    }

    private RestClient buildRestClient(String url) {
        return RestClient.builder().baseUrl(url).build();
    }

    private <T> T parseJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("解析JSON失败: {}", json, e);
            throw new RuntimeException("解析响应失败: " + e.getMessage(), e);
        }
    }
}
