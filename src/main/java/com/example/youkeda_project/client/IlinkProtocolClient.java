package com.example.youkeda_project.client;

import com.example.youkeda_project.protocol.CDNMedia;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.ImageItem;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.QRCodeResponse;
import com.example.youkeda_project.protocol.SendMessageRequest;
import com.example.youkeda_project.protocol.VoiceItem;
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

/**
 * iLink 协议 HTTP 客户端。
 * <p>
 * 自实现 iLink 协议所有关键接口，无官方 SDK：
 * - 扫码登录：getQRCode → pollQRStatus
 * - 消息拉取：getUpdates（长轮询 35s）
 * - 消息发送：sendMessage（文本/图片）
 * - 图片收发完整链路：通义万相生图 → OSS 下载 → AES-128-ECB 加密 → CDN 上传 → 发送 image_item
 * - 图片接收：CDN 下载 → deriveAesKey() 兼容 3 种 key 格式 → AES 解密 → 传给通义千问 VL
 * <p>
 * 所有 HTTP 请求/响应使用 byte[] + UTF-8 编解码，避免 Windows 控制台 GBK 编码污染 JSON。
 */
@Component
public class IlinkProtocolClient {

    private static final Logger log = LoggerFactory.getLogger(IlinkProtocolClient.class);
    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String CDN_BASE = "https://novac2c.cdn.weixin.qq.com/c2c";
    private static final int LONG_POLL_TIMEOUT_MS = 35000;

    private final ObjectMapper objectMapper;

    private RestClient restClient;
    private String botToken;       // 登录后获取的 Bot 鉴权 Token
    private String baseUrl = DEFAULT_BASE_URL;

    public IlinkProtocolClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = buildRestClient(DEFAULT_BASE_URL);
    }

    /**
     * 设置登录后的凭据（botToken + baseUrl），baseUrl 变化时重建 RestClient。
     */
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

    /**
     * 获取登录二维码 bot_type=3 表示第三方 Bot。
     */
    public QRCodeResponse getQRCode() {
        String json = restClient.get()
                .uri("/ilink/bot/get_bot_qrcode?bot_type=3")
                .retrieve()
                .body(String.class);
        return parseJson(json, QRCodeResponse.class);
    }

    /**
     * 轮询二维码扫码状态。
     */
    public LoginResult pollQRStatus(String qrcode) {
        String json = restClient.get()
                .uri("/ilink/bot/get_qrcode_status?qrcode=" + qrcode)
                .retrieve()
                .body(String.class);
        return parseJson(json, LoginResult.class);
    }

    /**
     * 长轮询拉取消息（35s 超时），通过 cursor 实现增量拉取。
     */
    public GetUpdatesResponse getUpdates(String cursor) {
        String body = "{\"get_updates_buf\":\"" + (cursor != null ? cursor : "") + "\"}";
        String json = postWithAuth("/ilink/bot/getupdates", body, LONG_POLL_TIMEOUT_MS);
        return parseJson(json, GetUpdatesResponse.class);
    }

    /**
     * 发送文本消息（可包含多项内容，如图文混排）。
     * 使用 SendMessageRequest.items() 构造请求体。
     */
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

    /**
     * 从微信 CDN 下载图片并 AES-ECB 解密。
     * 优先使用 fullUrl，否则使用 encryptQueryParam 拼装下载 URL。
     * AES key 可能出现在 ImageItem.aeskey 或 CDNMedia.aesKey 中，两处都检查。
     */
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

        // AES key 可能在 ImageItem 的 aeskey 字段或在 CDNMedia 的 aesKey 字段中
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
            // AES-128-ECB 解密，去除 PKCS5Padding
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

    /**
     * 将 AES key 从不同格式统一转换为 16 字节密钥。
     * 兼容格式：
     * 1. 32 位 hex 字符串直接解析
     * 2. base64 解码后是 32 字节 hex 字符串
     * 3. base64 解码后直接是 16 字节密钥
     */
    private byte[] deriveAesKey(String aesKeyStr) {
        if (aesKeyStr == null) throw new IllegalArgumentException("AES key为空");

        // 情况 1: 32 位 hex 字符串
        if (aesKeyStr.matches("^[0-9a-fA-F]{32}$")) {
            return HexFormat.of().parseHex(aesKeyStr);
        }

        // 尝试 base64 解码
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(aesKeyStr);
        } catch (Exception e) {
            throw new RuntimeException("无法解析AES key: " + aesKeyStr);
        }

        // 情况 2: 解码后是一段 32 位 hex 字符串（如 "aabb..." 的 ASCII 字节）
        if (decoded.length == 32) {
            String asHex = new String(decoded, StandardCharsets.UTF_8);
            if (asHex.matches("^[0-9a-fA-F]{32}$")) {
                return HexFormat.of().parseHex(asHex);
            }
        }

        // 情况 3: 解码后直接就是 16 字节密钥
        if (decoded.length == 16) {
            return decoded;
        }

        throw new RuntimeException("无法解析AES key，长度不匹配: " + aesKeyStr);
    }

    /**
     * 完整图片发送链路：
     * 1. 生成随机 AES 密钥
     * 2. 调用 getUploadUrl 获取 CDN 上传地址
     * 3. AES-128-ECB 加密图片字节
     * 4. POST 密文到 CDN
     * 5. 从响应 header（x-encrypted-param）获取加密查询参数
     * 6. 构造 CDNMedia + SendMessageRequest 发送 image_item
     */
    public void uploadAndSendImage(String toUserId, String contextToken, byte[] imageBytes) {
        try {
            // 步骤 1: 生成随机 AES 密钥（16 字节）
            byte[] aesKeyBytes = new byte[16];
            ThreadLocalRandom.current().nextBytes(aesKeyBytes);
            String aesKeyHex = HexFormat.of().formatHex(aesKeyBytes);

            String fileKey = generateFileKey();

            // 步骤 2: 计算 MD5 和补齐后的文件大小（AES 要求 16 字节对齐）
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

            // 步骤 3+4: AES 加密后上传到 CDN
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"));
            byte[] encryptedBytes = cipher.doFinal(imageBytes);

            String uploadFullUrl = uploadResp.has("upload_full_url")
                ? uploadResp.get("upload_full_url").asText() : null;
            String uploadParam = uploadResp.has("upload_param")
                ? uploadResp.get("upload_param").asText() : null;

            // 步骤 5: 上传 CDN 后从 response header 获取 x-encrypted-param
            String encryptQueryParam = uploadToCdn(encryptedBytes, uploadFullUrl, uploadParam, fileKey);

            // 步骤 6: 构造 CDNMedia + 发送 image_item
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

    /**
     * 将 AES 加密后的图片字节 POST 到微信 CDN。
     * 支持两种上传方式：upload_full_url（直连）或 CDN_BASE + upload_param。
     * 上传成功后从响应 header 中获取 x-encrypted-param。
     */
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

    /**
     * 生成随机 16 字节 hex 字符串作为 fileKey。
     */
    private String generateFileKey() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 带鉴权的 POST 请求。
     * 使用 byte[] + UTF-8 编解码避免 GBK 编码污染。
     * 必须携带 Authorization + X-WECHAT-UIN 两个 HTTP header。
     */
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

    /**
     * 从 VoiceItem 中提取公开可访问的 CDN 下载 URL（用于传给外部 ASR 服务）。
     * <p>
     * 优先通过 media.encrypt_query_param 拼接 URL（避免 fullUrl 中的 taskid 等追踪参数），
     * 否则使用 media.fullUrl 并去除 taskid，最后降级到 voice_url 直链。
     */
    public String getVoiceDownloadUrl(VoiceItem voiceItem) {
        if (voiceItem == null) {
            throw new RuntimeException("语音项为空");
        }

        CDNMedia media = voiceItem.media();

        // 优先用 encryptQueryParam 构造，避免 fullUrl 里的 taskid 等额外参数
        if (media != null && media.encryptQueryParam() != null && !media.encryptQueryParam().isBlank()) {
            return CDN_BASE + "/download?encrypted_query_param="
                + URLEncoder.encode(media.encryptQueryParam(), StandardCharsets.UTF_8);
        }

        if (media != null && media.fullUrl() != null && !media.fullUrl().isBlank()) {
            return stripTrackingParams(media.fullUrl());
        }

        if (voiceItem.voiceUrl() != null && !voiceItem.voiceUrl().isBlank()) {
            return voiceItem.voiceUrl();
        }

        throw new RuntimeException("语音无下载 URL（media 和 voice_url 均为空）");
    }

    /**
     * 去掉 URL 中的 taskid 等追踪参数，缩短 URL 长度。
     */
    private String stripTrackingParams(String url) {
        try {
            URI uri = URI.create(url);
            String query = uri.getQuery();
            if (query == null || query.isBlank()) {
                return url;
            }
            StringBuilder newQuery = new StringBuilder();
            for (String param : query.split("&")) {
                if (param.startsWith("taskid=")) continue;
                if (!newQuery.isEmpty()) newQuery.append("&");
                newQuery.append(param);
            }
            return uri.getScheme() + "://" + uri.getHost() + uri.getPath()
                + (newQuery.isEmpty() ? "" : "?" + newQuery);
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 从微信 CDN 下载语音文件并 AES-ECB 解密（和图片下载逻辑相同）。
     * <p>
     * 优先从 VoiceItem.media 构造 CDN 下载 URL（fullUrl 或 encrypt_query_param），
     * 降级到 voiceUrl 直链。AES key 从 media.aesKey 获取。
     */
    public byte[] downloadVoice(VoiceItem voiceItem) {
        String url = getVoiceDownloadUrl(voiceItem);

        log.info("下载语音: {}", url);
        byte[] encryptedBytes = RestClient.create().get()
            .uri(URI.create(url))
            .retrieve()
            .body(byte[].class);
        log.info("语音下载完成: {} bytes", encryptedBytes != null ? encryptedBytes.length : 0);

        // 如果有 AES key 则解密，否则直接返回原始字节
        CDNMedia media = voiceItem.media();
        if (media == null || media.aesKey() == null || media.aesKey().isBlank()) {
            return encryptedBytes;
        }

        try {
            byte[] key = deriveAesKey(media.aesKey());
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] decrypted = cipher.doFinal(encryptedBytes);
            int pad = decrypted[decrypted.length - 1];
            if (pad > 0 && pad <= 16) {
                decrypted = Arrays.copyOf(decrypted, decrypted.length - pad);
            }

            // 记录原始和已解密前 16 字节以便比较 AES 是否必要
            StringBuilder rawHex = new StringBuilder();
            for (int i = 0; i < Math.min(16, encryptedBytes.length); i++) {
                rawHex.append(String.format("%02X ", encryptedBytes[i]));
            }
            StringBuilder decHex = new StringBuilder();
            for (int i = 0; i < Math.min(16, decrypted.length); i++) {
                decHex.append(String.format("%02X ", decrypted[i]));
            }
            log.info("语音原始字节(hex): {} | 解密后(hex): {}", rawHex, decHex);
            log.info("语音解密后前32字符: {}", new String(decrypted, 0, Math.min(32, decrypted.length), StandardCharsets.ISO_8859_1));

            return decrypted;
        } catch (Exception e) {
            throw new RuntimeException("语音 AES 解密失败", e);
        }
    }

    /**
     * 将音频字节上传到微信 CDN，返回可公开访问的下载 URL。
     * 用于语音识别降级策略：当原始语音 URL 需要鉴权无法被 ASR 服务访问时，
     * 先通过 CDN 上传获取公开 URL，再传给 dashscope。
     */
    public String uploadAudioToCdn(byte[] audioBytes, String toUserId) {
        try {
            byte[] aesKeyBytes = new byte[16];
            ThreadLocalRandom.current().nextBytes(aesKeyBytes);
            String aesKeyHex = HexFormat.of().formatHex(aesKeyBytes);

            String fileKey = generateFileKey();

            byte[] md5Bytes = MessageDigest.getInstance("MD5").digest(audioBytes);
            String md5Hex = HexFormat.of().formatHex(md5Bytes);

            int rawSize = audioBytes.length;
            int paddedSize = (rawSize / 16 + 1) * 16;

            Map<String, Object> uploadReq = Map.of(
                "filekey", fileKey,
                "media_type", 3, // 3=audio
                "to_user_id", toUserId,
                "rawsize", rawSize,
                "rawfilemd5", md5Hex,
                "filesize", paddedSize,
                "no_need_thumb", true,
                "aeskey", aesKeyHex,
                "base_info", Map.of("channel_version", "1.0.2")
            );

            String uploadReqJson = objectMapper.writeValueAsString(uploadReq);
            log.info("getUploadUrl(音频)请求: {}", uploadReqJson);
            String uploadRespJson = postWithAuth("/ilink/bot/getuploadurl", uploadReqJson, 15000);
            log.info("getUploadUrl(音频)响应: {}", uploadRespJson);

            JsonNode uploadResp = objectMapper.readTree(uploadRespJson);
            JsonNode baseResp = uploadResp.path("base_resp");
            if (baseResp.has("ret") && baseResp.get("ret").asInt() != 0) {
                throw new RuntimeException("获取上传URL失败: ret=" + baseResp.get("ret"));
            }

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"));
            byte[] encryptedBytes = cipher.doFinal(audioBytes);

            String uploadFullUrl = uploadResp.has("upload_full_url")
                ? uploadResp.get("upload_full_url").asText() : null;
            String uploadParam = uploadResp.has("upload_param")
                ? uploadResp.get("upload_param").asText() : null;

            String encryptQueryParam = uploadToCdn(encryptedBytes, uploadFullUrl, uploadParam, fileKey);

            // 构造 CDN 下载 URL（含 encrypt_query_param，公开可访问）
            String cdnDownloadUrl = CDN_BASE + "/download?encrypted_query_param="
                + URLEncoder.encode(encryptQueryParam, StandardCharsets.UTF_8);
            log.info("音频已上传到CDN: {}", cdnDownloadUrl);
            return cdnDownloadUrl;

        } catch (Exception e) {
            throw new RuntimeException("音频 CDN 上传失败", e);
        }
    }

    /**
     * 生成随机 X-WECHAT-UIN header，iLink 协议要求的客户端标识。
     * 将 int 转 unsigned decimal string，再 base64 编码。
     */
    private String generateWechatUin() {
        int random = ThreadLocalRandom.current().nextInt();
        String decimal = Integer.toUnsignedString(random);
        return Base64.getEncoder().encodeToString(decimal.getBytes(StandardCharsets.UTF_8));
    }

    private RestClient buildRestClient(String url) {
        return RestClient.builder().baseUrl(url).build();
    }

    /**
     * 通用 JSON 反序列化，统一异常处理。
     */
    private <T> T parseJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("解析JSON失败: {}", json, e);
            throw new RuntimeException("解析响应失败: " + e.getMessage(), e);
        }
    }
}
