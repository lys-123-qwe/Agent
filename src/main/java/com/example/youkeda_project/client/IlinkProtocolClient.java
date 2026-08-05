package com.example.youkeda_project.client;

import com.example.youkeda_project.protocol.CDNMedia;
import com.example.youkeda_project.protocol.FileItem;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.ImageItem;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.QRCodeResponse;
import com.example.youkeda_project.protocol.SendMessageRequest;
import com.example.youkeda_project.protocol.VoiceItem;
import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * iLink protocol HTTP client.
 *
 * <p>Implements key iLink interfaces without official SDK:
 * - QR login: getQRCode → pollQRStatus
 * - Message polling: getUpdates (long poll 35s)
 * - Message sending: sendMessage (text/image)
 * - Image upload/download with AES-128-ECB encryption/decryption
 * - Voice download/upload helpers
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
     * 设置登录后的凭据(botToken + baseUrl),baseUrl 变化时重建 RestClient.
     */
    /** 设置登录后拿到的 Bot 授权 Token 与接口地址。 */
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
     * 获取登录二维码 bot_type=3 表示第三方 Bot.
     */
    /** 向微信申请一个登录二维码，返回二维码 ID 与图片内容。 */
    public QRCodeResponse getQRCode() {
        String json = restClient.get()
                .uri("/ilink/bot/get_bot_qrcode?bot_type=3")
                .retrieve()
                .body(String.class);
        return parseJson(json, QRCodeResponse.class);
    }

    /**
     * 轮询二维码扫码状态.
     */
    /** 轮询二维码状态（用户是否扫码/确认），返回登录结果。 */
    public LoginResult pollQRStatus(String qrcode) {
        String json = restClient.get()
                .uri("/ilink/bot/get_qrcode_status?qrcode=" + qrcode)
                .retrieve()
                .body(String.class);
        return parseJson(json, LoginResult.class);
    }

    /**
     * 长轮询拉取消息(35s 超时),通过 cursor 实现增量拉取.
     */
    /** 长轮询获取新消息，cursor 为消息游标（收到的进度）。 */
    public GetUpdatesResponse getUpdates(String cursor) {
        String body = "{\"get_updates_buf\":\"" + (cursor != null ? cursor : "") + "\"}";
        String json = postWithAuth("/ilink/bot/getupdates", body, LONG_POLL_TIMEOUT_MS);
        return parseJson(json, GetUpdatesResponse.class);
    }

    /**
     * 发送文本消息(可包含多项内容,如图文混排).
     * 使用 SendMessageRequest.items() 构造请求体.
     */
    /** 发送消息（文本/图片/语音/文件，由 items 决定类型）。 */
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
     * 从微信 CDN 下载图片并 AES-ECB 解密.
     * 优先使用 fullUrl,否则使用 encryptQueryParam 拼装下载 URL.
     * AES key 可能出现在 ImageItem.aeskey 或 CDNMedia.aesKey 中,两处都检查.
     */
    /** 下载微信图片并 AES 解密，返回真实图片字节。 */
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
        byte[] encryptedBytes = buildTimeoutRestClient().get()
            .uri(URI.create(url))
            .retrieve()
            .body(byte[].class);
        log.info("CDN下载完成: {} bytes", encryptedBytes != null ? encryptedBytes.length : 0);

        if (aesKeyStr == null) {
            log.warn("图片无AES key,返回原始密文");
            return encryptedBytes;
        }

        byte[] key = deriveAesKey(aesKeyStr);
        try {
            // AES-128-ECB 解密,去除 PKCS5Padding
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
     * 从微信 CDN 下载文件并 AES-ECB 解密.
     * 流程与 downloadAndDecryptImage 相同,仅入参类型不同.
     */
    /** 下载微信文件并 AES 解密，返回真实文件字节。 */
    public byte[] downloadAndDecryptFile(FileItem fileItem) {
        if (fileItem == null || fileItem.media() == null) {
            throw new RuntimeException("消息中没有文件");
        }

        String url = null;
        if (fileItem.media().fullUrl() != null && !fileItem.media().fullUrl().isBlank()) {
            url = fileItem.media().fullUrl();
        } else if (fileItem.media().encryptQueryParam() != null && !fileItem.media().encryptQueryParam().isBlank()) {
            url = CDN_BASE + "/download?encrypted_query_param="
                + URLEncoder.encode(fileItem.media().encryptQueryParam(), StandardCharsets.UTF_8);
        }
        if (url == null) throw new RuntimeException("文件无下载URL");

        String aesKeyStr = fileItem.media().aesKey();

        log.info("下载文件CDN: {} ({})", url, fileItem.fileName());
        byte[] encryptedBytes = buildTimeoutRestClient().get()
            .uri(URI.create(url))
            .retrieve()
            .body(byte[].class);

        if (aesKeyStr == null) {
            log.warn("文件无AES key,返回原始密文");
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
            throw new RuntimeException("文件AES解密失败", e);
        }
    }

    /**
     * 将 AES key 从不同格式统一转换为 16 字节密钥.
     * 兼容格式:
     * 1. 32 位 hex 字符串直接解析
     * 2. base64 解码后是 32 字节 hex 字符串
     * 3. base64 解码后直接是 16 字节密钥
     */
    /** 从 hex 字符串派生 16 字节 AES 密钥。 */
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

        // 情况 2: 解码后是一段 32 位 hex 字符串(如 "aabb..." 的 ASCII 字节)
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

        throw new RuntimeException("无法解析AES key,长度不匹配: " + aesKeyStr);
    }

    /**
     * 完整图片发送链路:
     * 1. 生成随机 AES 密钥
     * 2. 调用 getUploadUrl 获取 CDN 上传地址
     * 3. AES-128-ECB 加密图片字节
     * 4. POST 密文到 CDN
     * 5. 从响应 header(x-encrypted-param)获取加密查询参数
     * 6. 构造 CDNMedia + SendMessageRequest 发送 image_item
     */
    /** 上传图片到 CDN 并作为图片消息发送给用户。 */
    public void uploadAndSendImage(String toUserId, String contextToken, byte[] imageBytes) {
        try {
            UploadedMedia m = uploadMedia(1, imageBytes, toUserId);
            SendMessageRequest request = SendMessageRequest.cdnImage(toUserId, contextToken, m.media(), m.midSize());
            String msgBody = objectMapper.writeValueAsString(request);
            log.info("发送图片CDN消息: {}", msgBody);
            postWithAuth("/ilink/bot/sendmessage", msgBody, 15000);
        } catch (Exception e) {
            throw new RuntimeException("图片上传发送失败", e);
        }
    }

    /** 统一媒体上传流程：生成密钥 → getuploadurl → AES加密 → CDN上传 → 返回 CDNMedia 与密文大小。 */
    private UploadedMedia uploadMedia(int mediaType, byte[] data, String toUserId) {
        try {
            byte[] aesKeyBytes = new byte[16];
            ThreadLocalRandom.current().nextBytes(aesKeyBytes);
            String aesKeyHex = HexFormat.of().formatHex(aesKeyBytes);

            String fileKey = generateFileKey();

            byte[] md5Bytes = MessageDigest.getInstance("MD5").digest(data);
            String md5Hex = HexFormat.of().formatHex(md5Bytes);
            int rawSize = data.length;
            int paddedSize = (rawSize / 16 + 1) * 16;

            Map<String, Object> uploadReq = Map.of(
                "filekey", fileKey,
                "media_type", mediaType,
                "to_user_id", toUserId,
                "rawsize", rawSize,
                "rawfilemd5", md5Hex,
                "filesize", paddedSize,
                "no_need_thumb", true,
                "aeskey", aesKeyHex,
                "base_info", Map.of("channel_version", "1.0.2")
            );
            String uploadReqJson = objectMapper.writeValueAsString(uploadReq);
            log.info("getUploadUrl(media_type={})请求: {}", mediaType, uploadReqJson);
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
            byte[] encryptedBytes = cipher.doFinal(data);

            String uploadFullUrl = uploadResp.has("upload_full_url")
                ? uploadResp.get("upload_full_url").asText() : null;
            String uploadParam = uploadResp.has("upload_param")
                ? uploadResp.get("upload_param").asText() : null;
            String encryptQueryParam = uploadToCdn(encryptedBytes, uploadFullUrl, uploadParam, fileKey);

            String aesKeyBase64 = Base64.getEncoder().encodeToString(aesKeyHex.getBytes(StandardCharsets.UTF_8));
            return new UploadedMedia(new CDNMedia(encryptQueryParam, aesKeyBase64), encryptedBytes.length);
        } catch (Exception e) {
            throw new RuntimeException("媒体上传失败", e);
        }
    }

    /** 上传结果：CDN 媒体元数据 + AES 加密后的大小。 */
    private record UploadedMedia(CDNMedia media, int midSize) {}

    /**
     * 将 AES 加密后的图片字节 POST 到微信 CDN.
     * 支持两种上传方式:upload_full_url(直连)或 CDN_BASE + upload_param.
     * 上传成功后从响应 header 中获取 x-encrypted-param.
     */
    /** 把密文 POST 到 CDN，从响应头取 x-encrypted-param 返回。 */
    private String uploadToCdn(byte[] encryptedBytes, String uploadFullUrl, String uploadParam, String fileKey) {
        String cdnUrl;
        if (uploadFullUrl != null && !uploadFullUrl.isBlank()) {
            cdnUrl = uploadFullUrl;
        } else if (uploadParam != null && !uploadParam.isBlank()) {
            String encodedParam = URLEncoder.encode(uploadParam, StandardCharsets.UTF_8);
            String encodedKey = URLEncoder.encode(fileKey, StandardCharsets.UTF_8);
            cdnUrl = CDN_BASE + "/upload?encrypted_query_param=" + encodedParam + "&filekey=" + encodedKey;
        } else {
            throw new RuntimeException("getUploadUrl未返回upload_full_url或upload_param");
        }

        log.info("上传到CDN: {} (encrypted size: {} bytes)", cdnUrl, encryptedBytes.length);

        // 使用原生 HttpURLConnection + setFixedLengthStreamingMode，
        // 避免 Spring RestClient 对 byte[] body 可能使用的 chunked 传输编码导致 CDN 400
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(cdnUrl);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setFixedLengthStreamingMode(encryptedBytes.length);

            conn.getOutputStream().write(encryptedBytes);
            conn.getOutputStream().flush();

            int status = conn.getResponseCode();
            if (status >= 400) {
                byte[] errBytes = conn.getErrorStream() != null
                        ? conn.getErrorStream().readAllBytes() : new byte[0];
                String errText = new String(errBytes, StandardCharsets.UTF_8);
                log.error("CDN上传失败: status={}, body={}", status, errText);
                throw new RuntimeException("CDN上传失败 (" + status + "): " + errText);
            }

            String encryptQueryParam = conn.getHeaderField("x-encrypted-param");
            if (encryptQueryParam == null || encryptQueryParam.isBlank()) {
                throw new RuntimeException("CDN上传未返回x-encrypted-param header");
            }
            return encryptQueryParam;
        } catch (java.io.IOException e) {
            log.error("CDN上传网络错误: {}", e.getMessage());
            throw new RuntimeException("CDN上传网络错误: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 生成随机 16 字节 hex 字符串作为 fileKey.
     */
    /** 生成随机 16 字节 hex 作为文件唯一标识。 */
    private String generateFileKey() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 带鉴权的 POST 请求.
     * 使用 byte[] + UTF-8 编解码避免 GBK 编码污染.
     * 必须携带 Authorization + X-WECHAT-UIN 两个 HTTP header.
     */
    /** 带授权 Token 的 POST 请求，返回响应 JSON 字符串。 */
    private String postWithAuth(String path, String body, int timeoutMs) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("未登录,缺少 bot_token");
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
     * 从 VoiceItem 中提取公开可访问的 CDN 下载 URL(用于传给外部 ASR 服务).
     * <p>
     * 优先通过 media.encrypt_query_param 拼接 URL(避免 fullUrl 中的 taskid 等追踪参数),
     * 否则使用 media.fullUrl 并去除 taskid,最后降级到 voice_url 直链.
     */
    /** 从语音消息构造 CDN 下载 URL。 */
    public String getVoiceDownloadUrl(VoiceItem voiceItem) {
        if (voiceItem == null) {
            throw new RuntimeException("语音项为空");
        }

        CDNMedia media = voiceItem.media();

        // 优先用 encryptQueryParam 构造,避免 fullUrl 里的 taskid 等额外参数
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

        throw new RuntimeException("语音无下载 URL(media 和 voice_url 均为空)");
    }

    /**
     * 去掉 URL 中的 taskid 等追踪参数,缩短 URL 长度.
     */
    /** 去除 URL 中的追踪参数（如临时签名），保留可下载地址。 */
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
     * 从微信 CDN 下载语音文件并 AES-ECB 解密(和图片下载逻辑相同).
     * <p>
     * 优先从 VoiceItem.media 构造 CDN 下载 URL(fullUrl 或 encrypt_query_param),
     * 降级到 voiceUrl 直链.AES key 从 media.aesKey 获取.
     */
    /** 下载微信语音并 AES 解密，返回真实语音字节。 */
    public byte[] downloadVoice(VoiceItem voiceItem) {
        String url = getVoiceDownloadUrl(voiceItem);

        log.info("下载语音: {}", url);
        byte[] encryptedBytes = buildTimeoutRestClient().get()
            .uri(URI.create(url))
            .retrieve()
            .body(byte[].class);

        // 如果有 AES key 则解密,否则直接返回原始字节
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
            return decrypted;
        } catch (Exception e) {
            throw new RuntimeException("语音 AES 解密失败", e);
        }
    }

    /**
     * 完整语音发送链路(用于 TTS 语音合成结果发送):
     * 1. 生成随机 AES 密钥
     * 2. 调用 getUploadUrl 获取 CDN 上传地址,media_type=3(音频)
     * 3. AES-128-ECB 加密音频字节
     * 4. 用 HttpURLConnection POST 密文到 CDN(避免 RestClient 编码问题)
     * 5. 从响应 header(x-encrypted-param)获取加密查询参数
     * 6. 构造 CDNMedia + SendMessageRequest 发送 voice_item
     */
    /** 上传语音到 CDN 并作为语音消息发送（含时长）。 */
    public void uploadAndSendVoice(String toUserId, String contextToken, byte[] audioBytes, int durationSeconds) {
        try {
            UploadedMedia m = uploadMedia(3, audioBytes, toUserId);
            String voiceUrl = CDN_BASE + "/download?encrypted_query_param="
                + URLEncoder.encode(m.media().encryptQueryParam(), StandardCharsets.UTF_8);
            SendMessageRequest request = SendMessageRequest.cdnVoice(toUserId, contextToken,
                m.media(), voiceUrl, durationSeconds);
            String msgBody = objectMapper.writeValueAsString(request);
            log.info("发送语音CDN消息: {}", msgBody);
            postWithAuth("/ilink/bot/sendmessage", msgBody, 15000);
        } catch (Exception e) {
            throw new RuntimeException("语音上传发送失败", e);
        }
    }

    /**
     * 把文件上传到微信 CDN 并作为 file_item 发送.
     * <p>
     * iLink 文件消息结构(基于 weixin-ilink SDK 确认):
     * - getUploadUrl 时 media_type=3(FILE)
     * - sendMessage 时 item type=4(FILE)
     * - file_item 包含 media(CDNMedia),file_name,len(文件大小字符串)
     */
    /** 上传文件到 CDN 并作为文件消息发送。 */
    public void uploadAndSendFile(String toUserId, String contextToken, byte[] fileBytes,
                                   String fileName, long fileSize) {
        try {
            UploadedMedia m = uploadMedia(3, fileBytes, toUserId);
            SendMessageRequest request = SendMessageRequest.cdnFile(toUserId, contextToken,
                m.media(), fileName, fileSize);
            String msgBody = objectMapper.writeValueAsString(request);
            log.info("发送文件CDN消息: {}", msgBody);
            postWithAuth("/ilink/bot/sendmessage", msgBody, 15000);
        } catch (Exception e) {
            throw new RuntimeException("文件上传发送失败", e);
        }
    }

    /**
     * 生成随机 X-WECHAT-UIN header,iLink 协议要求的客户端标识.
     * 将 int 转 unsigned decimal string,再 base64 编码.
     */
    /** 生成随机 X-WECHAT-UIN 客户端标识（iLink 协议要求）。 */
    private String generateWechatUin() {
        int random = ThreadLocalRandom.current().nextInt();
        String decimal = Integer.toUnsignedString(random);
        return Base64.getEncoder().encodeToString(decimal.getBytes(StandardCharsets.UTF_8));
    }

    /** 构建指定地址的 RestClient。 */
    private RestClient buildRestClient(String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }

    /**
     * 创建一个带超时的 RestClient(用于 ad-hoc CDN 下载/上传等).
     */
    /** 构建带连接/读取超时的 RestClient（用于 CDN 下载上传）。 */
    private static RestClient buildTimeoutRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * 通用 JSON 反序列化,统一异常处理.
     */
    /** 通用 JSON 反序列化，统一异常处理。 */
    private <T> T parseJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("解析JSON失败: {}", json, e);
            throw new RuntimeException("解析响应失败: " + e.getMessage(), e);
        }
    }
}
