package com.example.youkeda_project.service;

<<<<<<< HEAD
import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.ChatResult;
import com.example.youkeda_project.ai.ConstellationTool;
import com.example.youkeda_project.ai.DateTimeTool;
import com.example.youkeda_project.ai.EditImageTool;
import com.example.youkeda_project.ai.ExchangeRateTool;
import com.example.youkeda_project.ai.ImageTool;
import com.example.youkeda_project.ai.MusicTool;
import com.example.youkeda_project.ai.PhoneLocationTool;
import com.example.youkeda_project.ai.RecommendMusicTool;
import com.example.youkeda_project.ai.RecognizeImageTool;
import com.example.youkeda_project.ai.ScheduledTaskTool;
import com.example.youkeda_project.ai.TravelTool;
import com.example.youkeda_project.ai.SearchTool;
import com.example.youkeda_project.ai.StockTool;
import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.ai.TranslationTool;
import com.example.youkeda_project.ai.TtsTool;
import com.example.youkeda_project.ai.WeatherTool;
import com.example.youkeda_project.client.IlinkProtocolClient;
import com.example.youkeda_project.config.UserContextStore;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.QRCodeResponse;
=======
>>>>>>> c9fdf72608b04178876c980cce583f737dbdee3d
import com.example.youkeda_project.protocol.FileItem;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.VoiceItem;
import com.example.youkeda_project.protocol.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WeChat iLink service — now delegates to {@link WeChatSessionManager} for
 * multi-session support.
 *
 * <p><b>Default session (backward-compatible):</b>
 * Calling {@link #login()} / {@link #startListening startListening(...)} / {@link #sendText sendText(...)}
 * without a sessionId operates on the "default" session — the first one created.</p>
 *
 * <p><b>Multi-session:</b>
 * Use the sessionId overloads for multi-bot scenarios. Each session has its own
 * IlinkProtocolClient, thread pool, QR code, and contextToken.</p>
 */
@Service
public class IlinkService {

    private static final Logger log = LoggerFactory.getLogger(IlinkService.class);

    private final WeChatSessionManager sessionManager;

<<<<<<< HEAD
    private final IlinkProtocolClient client;
    private final WeatherService weatherService;
    private final AIService aiService;
    private final ImageService imageService;
    private final VoiceService voiceService;
    private final TtsService ttsService;
    private final FileService fileService;
    private final WeatherTool weatherTool;
    private final ImageTool imageTool;
    private final TtsTool ttsTool;
    private final StockTool stockTool;
    private final ExchangeRateTool exchangeRateTool;
    private final DateTimeTool dateTimeTool;
    private final ConstellationTool constellationTool;
    private final PhoneLocationTool phoneLocationTool;
    private final SearchTool searchTool;
    private final MusicTool musicTool;
    private final RecommendMusicTool recommendMusicTool;
    private final TranslationTool translationTool;
    private final TravelTool travelTool;
    private final ScheduledTaskTool scheduledTaskTool;
    private final UserContextStore userContextStore;
=======
    public IlinkService(WeChatSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
>>>>>>> c9fdf72608b04178876c980cce583f737dbdee3d

    // ======================== Single-bot API (default session) ========================

    /**
     * Start login for the default session. Creates one if needed.
     *
     * @return QR code content
     */
    public synchronized String login() {
        WeChatSession existing = sessionManager.getDefaultSession();
        if (existing != null && existing.isLoggedIn() && existing.isListening()) {
            return "Already logged in as " + existing.getBotId()
                    + ". Use 'wechat login <label>' to add another bot.";
        }
        if (existing != null && existing.isLoggedIn() && !existing.isListening()) {
            log.info("Default session {} is logged in but not listening — replacing",
                    existing.getSessionId().substring(0, 8));
            sessionManager.closeSession(existing.getSessionId());
        }
        if (existing != null && existing.getStatus() == WeChatSession.Status.WAITING_QR) {
            return "QR login already in progress. Scan the QR code, or wait for timeout.";
        }

<<<<<<< HEAD
    public IlinkService(IlinkProtocolClient client, WeatherService weatherService,
                        AIService aiService, ImageService imageService, VoiceService voiceService,
                        TtsService ttsService, FileService fileService,
                        WeatherTool weatherTool, ImageTool imageTool, TtsTool ttsTool,
                        StockTool stockTool,
                        ExchangeRateTool exchangeRateTool, DateTimeTool dateTimeTool,
                        ConstellationTool constellationTool, PhoneLocationTool phoneLocationTool,
                        SearchTool searchTool,
                         MusicTool musicTool, RecommendMusicTool recommendMusicTool,
                          TranslationTool translationTool,
                          TravelTool travelTool,
                          ScheduledTaskTool scheduledTaskTool,
                          UserContextStore userContextStore) {
        this.client = client;
        this.weatherService = weatherService;
        this.aiService = aiService;
        this.imageService = imageService;
        this.voiceService = voiceService;
        this.ttsService = ttsService;
        this.fileService = fileService;
        this.weatherTool = weatherTool;
        this.imageTool = imageTool;
        this.ttsTool = ttsTool;
        this.stockTool = stockTool;
        this.exchangeRateTool = exchangeRateTool;
        this.dateTimeTool = dateTimeTool;
        this.constellationTool = constellationTool;
        this.phoneLocationTool = phoneLocationTool;
        this.searchTool = searchTool;
        this.musicTool = musicTool;
        this.recommendMusicTool = recommendMusicTool;
        this.translationTool = translationTool;
        this.travelTool = travelTool;
        this.scheduledTaskTool = scheduledTaskTool;
        this.userContextStore = userContextStore;
=======
        WeChatSessionManager.LoginResponse resp = sessionManager.startLogin("default");
        sessionManager.setDefaultSessionId(resp.sessionId());
        log.info("Default session {} created, QR ready", resp.sessionId());
        return resp.qrContent();
>>>>>>> c9fdf72608b04178876c980cce583f737dbdee3d
    }

    /**
     * Start login with a custom label (for multi-bot).
     */
    public WeChatSessionManager.LoginResponse login(String label) {
        return sessionManager.startLogin(label);
    }

    /**
     * Always create a fresh login session and make it the default.
     */
    public WeChatSessionManager.LoginResponse loginNew() {
        var resp = sessionManager.startLogin("default");
        sessionManager.setDefaultSessionId(resp.sessionId());
        return resp;
    }

    public com.example.youkeda_project.protocol.LoginResult waitForLogin(long timeoutMs) throws Exception {
        String sid = sessionManager.getDefaultSessionId();
        if (sid == null) throw new IllegalStateException("No login in progress. Call login() first.");
        return sessionManager.waitForLogin(sid, timeoutMs);
    }

    public com.example.youkeda_project.protocol.LoginResult waitForLogin(String sessionId, long timeoutMs) throws Exception {
        return sessionManager.waitForLogin(sessionId, timeoutMs);
    }

    // ---- messaging (default session) ----

    public List<WeixinMessage> getUpdates() {
        String sid = requireDefaultSession();
        try {
            return sessionManager.getUpdates(sid);
        } catch (java.io.IOException e) {
            throw new RuntimeException("getUpdates failed: " + e.getMessage(), e);
        }
    }

<<<<<<< HEAD
    /**
     * 启动消息轮询线程（daemon）。
     * 使用 getUpdates 长轮询拉取消息，将 cursor 带回以实现增量拉取。
     * 对每条消息提取文本/图片，检查有 contextToken 后交给 handleMessage 处理。
     */
    private void startPolling() {
        polling.set(true);
        pollThread = new Thread(() -> {
            log.info("消息轮询已启动");
            String cursor = "";

            while (polling.get() && connected.get()) {
                try {
                    GetUpdatesResponse resp = client.getUpdates(cursor);
                    if (resp.getUpdatesBuf() != null) {
                        cursor = resp.getUpdatesBuf();
                    }
                    if (resp.msgs() != null && !resp.msgs().isEmpty()) {
                        log.info("收到 {} 条消息", resp.msgs().size());
                        for (WeixinMessage msg : resp.msgs()) {
                            String fromUser = msg.fromUserId();
                            String text = extractText(msg);
                            boolean hasImage = extractFirstImage(msg) != null;
                            boolean hasVoice = extractFirstVoice(msg) != null;
                            boolean hasFile = extractFirstFile(msg) != null;
                            // 只处理有 contextToken 的消息
                            if (msg.contextToken() != null && (text != null || hasImage || hasVoice || hasFile)) {
                                if (text != null) {
                                    log.info("微信消息 from={}, text={}", fromUser, text);
                                    System.out.println("\n[微信] " + fromUser + ": " + text);
                                }
                                if (hasImage) {
                                    log.info("微信消息 from={}, [图片]", fromUser);
                                    System.out.println("\n[微信] " + fromUser + ": [图片]");
                                }
                                if (hasVoice) {
                                    log.info("微信消息 from={}, [语音]", fromUser);
                                    System.out.println("\n[微信] " + fromUser + ": [语音]");
                                }
                                if (hasFile) {
                                    log.info("微信消息 from={}, [文件]", fromUser);
                                    System.out.println("\n[微信] " + fromUser + ": [文件]");
                                }
                                userContextStore.updateToken(fromUser, msg.contextToken());
                                handleMessage(msg);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (polling.get()) {
                        log.warn("轮询异常: {}", e.getMessage());
                        try {
                            Thread.sleep(POLL_INTERVAL_MS * 2);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            log.info("消息轮询已停止");
        }, "ilink-poll");
        pollThread.setDaemon(true);
        pollThread.start();
=======
    public List<WeixinMessage> getUpdates(String sessionId) throws java.io.IOException {
        return sessionManager.getUpdates(sessionId);
>>>>>>> c9fdf72608b04178876c980cce583f737dbdee3d
    }

    public void sendText(String userId, String contextToken, String text) {
        try {
<<<<<<< HEAD
            String fromUser = msg.fromUserId();

            // 图片处理：不再直接识别，交由 DeepSeek 判断（识图 or 编辑）
            MessageItem imageItem = extractFirstImage(msg);
            if (imageItem != null) {
                byte[] imageBytes = client.downloadAndDecryptImage(imageItem);
                String textPrompt = extractText(msg);

                if (textPrompt != null && !textPrompt.isBlank()) {
                    // 同一条消息中带了文字指令 → 直接处理
                    System.out.println("[微信] " + fromUser + " 发送了图片+指令，正在处理...");
                    processImageWithInstruction(fromUser, msg.contextToken(), imageBytes, textPrompt);
                } else {
                    // 纯图片 → 存储等待文字指令
                    pendingImages.put(fromUser, imageBytes);
                    client.sendMessage(fromUser, msg.contextToken(),
                        List.of(MessageItem.text("图片已收到，请发送文字告诉我你想做什么？\n- 识别图片内容\n- 编辑图片（如：把猫换成狗、改变背景颜色）")));
                    log.info("已存储 {} 的待处理图片，等待指令", fromUser);
                    System.out.println("[微信] " + fromUser + ": [系统] 图片已收到，请发送文字告诉我你想做什么");
                }
                return;
            }

            // 语音识别：下载 → ASR 转文字 → AI 回复
            MessageItem voiceMsgItem = extractFirstVoice(msg);
            if (voiceMsgItem != null) {
                VoiceItem voiceItem = voiceMsgItem.voiceItem();
                System.out.println("[微信] " + fromUser + " 发送了语音，正在识别...");
                byte[] audioBytes = client.downloadVoice(voiceItem);
                String transcribedText = voiceService.transcribe(audioBytes);
                if (transcribedText.startsWith("错误:")) {
                    log.warn("语音识别失败: {}", transcribedText);
                    System.out.println("[微信] " + fromUser + " 语音识别失败: " + transcribedText);
                    // 发送友好的错误提示给用户
                    String userMessage = getVoiceErrorMessage(transcribedText);
                    client.sendMessage(fromUser, msg.contextToken(),
                        List.of(MessageItem.text(userMessage)));
                    return;
                }
                log.info("语音转文字结果: {}", transcribedText);
                System.out.println("[微信] " + fromUser + " (语音转文字): " + transcribedText);

                // 检查是否有待处理的图片（先发图，再发语音指令）
                byte[] pendingImage = pendingImages.remove(fromUser);
                if (pendingImage != null) {
                    processImageWithInstruction(fromUser, msg.contextToken(), pendingImage, transcribedText);
                    return;
                }

                // 检查是否有最近生成的图片可识别/编辑
                String lastGenUrl = lastGeneratedImageUrl.get(fromUser);
                if (lastGenUrl != null) {
                    System.out.println("[系统] 找到最近生成的图片，下载中...");
                    try {
                        byte[] genImageBytes = imageService.downloadImage(lastGenUrl);
                        processImageWithInstruction(fromUser, msg.contextToken(), genImageBytes, transcribedText);
                        return;
                    } catch (Exception e) {
                        log.warn("下载最近生成的图片失败，回退到普通对话: {}", e.getMessage());
                        lastGeneratedImageUrl.remove(fromUser);
                    }
                }

                // 无图片上下文，走普通对话
                List<Tool> voiceTools = List.of(weatherTool, imageTool, ttsTool, searchTool, stockTool, musicTool, recommendMusicTool, scheduledTaskTool);
                ChatResult result = aiService.chat(fromUser, transcribedText, voiceTools);
                String reply = result.getReplyText();

                if (result.hasPendingImage()) {
                    String imageUrl = result.getFirstPendingImageUrl();
                    lastGeneratedImageUrl.put(fromUser, imageUrl);
                    System.out.println("[系统] 图片已生成，正在上传到微信CDN...");
                    try {
                        byte[] imageBytes = imageService.downloadImage(imageUrl);
                        client.uploadAndSendImage(fromUser, msg.contextToken(), imageBytes);
                        log.info("已发送图片 {}", fromUser);
                        System.out.println("[系统] 图片已发送到微信");
                    } catch (Exception e) {
                        log.error("图片上传发送失败", e);
                        reply += "\n\n（图片上传失败: " + e.getMessage() + "）";
                    }
                }

                if (result.hasPendingAudio()) {
                    ToolResult audio = result.getFirstPendingAudio();
                    System.out.println("[系统] 语音合成完成，正在上传发送...");
                    try {
                        String fileName = audio.getAudioFileName() != null ? audio.getAudioFileName() : "audio.mp3";
                        client.uploadAndSendFile(fromUser, msg.contextToken(),
                            audio.getAudioBytes(), fileName, audio.getAudioBytes().length);
                        log.info("已发送音频文件 {} ({}s): {}", fromUser, audio.getAudioDurationSeconds(), fileName);
                        System.out.println("[微信] " + fromUser + ": [音频文件] " + fileName);
                    } catch (Exception e) {
                        log.error("音频文件上传发送失败", e);
                        reply += "\n\n（音频文件上传失败: " + e.getMessage() + "）";
                    }
                }

                client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(reply)));
                log.info("已回复语音 {}: {}", fromUser, reply);
                System.out.println("[微信] " + fromUser + ": " + reply);
                return;
            }

            // 文件识别：下载 → 提取文本 → AI 总结回复
            MessageItem fileMsgItem = extractFirstFile(msg);
            if (fileMsgItem != null) {
                FileItem fileItem = fileMsgItem.fileItem();
                String fileName = fileItem.fileName();
                long fileSize = 0;
                try { fileSize = Long.parseLong(fileItem.len()); } catch (NumberFormatException ignored) {}
                System.out.println("[微信] " + fromUser + " 发送了文件: " + fileName + " (" + fileSize + " bytes)");
                if (fileSize > fileService.getMaxFileSize()) {
                    client.sendMessage(fromUser, msg.contextToken(),
                        List.of(MessageItem.text("文件太大了，我只能处理 20MB 以内的文件")));
                    return;
                }
                try {
                    byte[] fileBytes = client.downloadAndDecryptFile(fileItem);
                    System.out.println("[系统] 文件下载完成: " + fileBytes.length + " bytes，正在解析...");
                    String extractedText = fileService.extractText(fileBytes, fileName);
                    log.info("文件文本提取完成: {}, {} 字符", fileName, extractedText.length());
                    System.out.println("[系统] 文本提取完成: " + extractedText.length() + " 字符");
                    String reply = aiService.generateWithContext(fromUser,
                        "请帮我总结这个文件的内容", "文件名：" + fileName + "\n\n文件内容：\n" + extractedText);
                    client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(reply)));
                    log.info("已回复文件识别 {}: {}", fromUser, reply);
                    System.out.println("[微信] " + fromUser + ": " + reply);
                } catch (Exception e) {
                    log.error("文件处理失败: {}", fileName, e);
                    client.sendMessage(fromUser, msg.contextToken(),
                        List.of(MessageItem.text("文件处理失败: " + e.getMessage())));
                }
                return;
            }

            String text = extractText(msg);

            // 检查是否有待处理的图片（用户先发图，再发文字指令）
            byte[] pendingImage = pendingImages.remove(fromUser);
            if (pendingImage != null) {
                processImageWithInstruction(fromUser, msg.contextToken(), pendingImage, text);
                return;
            }

            // Function Calling：将所有工具传递给 DeepSeek，由模型自主决定调用哪些
            System.out.println("[系统] " + fromUser + " 正在等待 AI 回复...");
            List<Tool> allTools = List.of(weatherTool, imageTool, ttsTool, searchTool, stockTool,
                exchangeRateTool, dateTimeTool, constellationTool, phoneLocationTool,
                musicTool, recommendMusicTool, translationTool, travelTool, scheduledTaskTool);
            ChatResult result = aiService.chat(fromUser, text, allTools);
            String reply = result.getReplyText();

            // 处理工具产生的媒体侧效应（图片 CDN 上传、音频文件发送）
            if (result.hasPendingImage()) {
                String imageUrl = result.getFirstPendingImageUrl();
                lastGeneratedImageUrl.put(fromUser, imageUrl);
                System.out.println("[系统] 图片已生成，正在上传到微信CDN...");
                try {
                    byte[] imageBytes = imageService.downloadImage(imageUrl);
                    client.uploadAndSendImage(fromUser, msg.contextToken(), imageBytes);
                    log.info("已发送图片 {}", fromUser);
                    System.out.println("[系统] 图片已发送到微信");
                } catch (Exception e) {
                    log.error("图片上传发送失败", e);
                    reply += "\n\n（图片上传失败: " + e.getMessage() + "）";
                }
            }

            if (result.hasPendingAudio()) {
                ToolResult audio = result.getFirstPendingAudio();
                System.out.println("[系统] 语音合成完成，正在上传发送...");
                try {
                    String fileName = audio.getAudioFileName() != null ? audio.getAudioFileName() : "audio.mp3";
                    client.uploadAndSendFile(fromUser, msg.contextToken(),
                        audio.getAudioBytes(), fileName, audio.getAudioBytes().length);
                    log.info("已发送音频文件 {} ({}s): {}", fromUser, audio.getAudioDurationSeconds(), fileName);
                    System.out.println("[微信] " + fromUser + ": [音频文件] " + fileName);
                } catch (Exception e) {
                    log.error("音频文件上传发送失败", e);
                    reply += "\n\n（音频文件上传失败: " + e.getMessage() + "）";
                }
            }

            // 首条消息附带欢迎语
            if (!ownerWelcomed && ownerId != null && ownerId.equals(fromUser)) {
                reply = WELCOME_TEXT + "\n\n" + reply;
                ownerWelcomed = true;
                log.info("已向 {} 发送欢迎语", fromUser);
            }

            // 发送文本回复
            client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(reply)));
            log.info("已回复 {}: {}", fromUser, reply);
            System.out.println("[微信] " + fromUser + ": " + reply);

        } catch (Exception e) {
            log.error("处理消息失败", e);
=======
            sessionManager.sendText(requireDefaultSession(), userId, contextToken, text);
        } catch (java.io.IOException e) {
            throw new RuntimeException("sendText failed: " + e.getMessage(), e);
>>>>>>> c9fdf72608b04178876c980cce583f737dbdee3d
        }
    }

    public void sendText(String sessionId, String userId, String contextToken, String text) throws java.io.IOException {
        sessionManager.sendText(sessionId, userId, contextToken, text);
    }

<<<<<<< HEAD
    /**
     * 处理带图片的指令：由 DeepSeek 通过 Function Calling 判断是识图还是编辑，
     * 调用对应的工具（RecognizeImageTool / EditImageTool），处理媒体结果。
     */
    private void processImageWithInstruction(String fromUser, String contextToken, byte[] imageBytes, String instruction) {
        System.out.println("[系统] " + fromUser + " 正在处理图片指令...");

        RecognizeImageTool recognizeTool = new RecognizeImageTool(aiService, fromUser, imageBytes);
        EditImageTool editTool = new EditImageTool(imageService, imageBytes);
        List<Tool> allTools = List.of(weatherTool, imageTool, ttsTool, searchTool,
            musicTool, recommendMusicTool, recognizeTool, editTool, scheduledTaskTool);

        String contextMessage = "[系统提示：当前有一张待处理的图片。请根据用户的指令选择合适的工具。]\n"
            + "[如果用户想识别图片内容（这是什么、描述图片、提取文字等），请调用 recognize_image 工具]\n"
            + "[如果用户想编辑图片（修改物体、颜色、背景、风格等），请调用 edit_image 工具]\n"
            + "用户指令：" + instruction;

        ChatResult result = aiService.chat(fromUser, contextMessage, allTools);
        String reply = result.getReplyText();

        // 处理编辑后的图片（CDN 上传 → 发送到微信）
        if (result.hasPendingImage()) {
            String imageUrl = result.getFirstPendingImageUrl();
            lastGeneratedImageUrl.put(fromUser, imageUrl);
            System.out.println("[系统] 图片编辑完成，正在上传到微信CDN...");
            try {
                byte[] editedImageBytes = imageService.downloadImage(imageUrl);
                client.uploadAndSendImage(fromUser, contextToken, editedImageBytes);
                log.info("已发送编辑后的图片 {}", fromUser);
                System.out.println("[系统] 图片已发送到微信");
            } catch (Exception e) {
                log.error("编辑图片上传发送失败", e);
                reply += "\n\n（编辑图片上传失败: " + e.getMessage() + "）";
            }
        }

        // 发送文本回复
        client.sendMessage(fromUser, contextToken, List.of(MessageItem.text(reply)));
        log.info("已回复图片指令 {}: {}", fromUser, reply);
        System.out.println("[微信] " + fromUser + ": " + reply);
=======
    public void sendImage(String userId, String contextToken, byte[] imageBytes) {
        sessionManager.sendImage(requireDefaultSession(), userId, contextToken, imageBytes);
>>>>>>> c9fdf72608b04178876c980cce583f737dbdee3d
    }

    public void sendImage(String sessionId, String userId, String contextToken, byte[] imageBytes) {
        sessionManager.sendImage(sessionId, userId, contextToken, imageBytes);
    }

    public void sendVoice(String userId, String contextToken, byte[] audioBytes, int durationSeconds) {
        sessionManager.sendVoice(requireDefaultSession(), userId, contextToken, audioBytes, durationSeconds);
    }

    public void sendFile(String userId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        sessionManager.sendFile(requireDefaultSession(), userId, contextToken, fileBytes, fileName, fileSize);
    }

    public void sendFile(String sessionId, String userId, String contextToken, byte[] fileBytes, String fileName, long fileSize) {
        sessionManager.sendFile(sessionId, userId, contextToken, fileBytes, fileName, fileSize);
    }

    // ---- listener (default session) ----

    public synchronized void startListening(String sessionId,
                                            WeChatSession.MessageHandler textHandler,
                                            WeChatSession.ImageMessageHandler imageHandler,
                                            WeChatSession.VoiceMessageHandler voiceHandler,
                                            WeChatSession.FileMessageHandler fileHandler) {
        sessionManager.startListening(sessionId, textHandler, imageHandler, voiceHandler, fileHandler);
    }

    public void stopListening() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid != null) sessionManager.stopListening(sid);
    }

    public void stopListening(String sessionId) {
        sessionManager.stopListening(sessionId);
    }

    // ---- status (default session) ----

    public boolean isConnected() {
        WeChatSession s = sessionManager.getDefaultSession();
        return s != null && s.isLoggedIn();
    }

    public boolean isConnected(String sessionId) {
        WeChatSession s = sessionManager.getSession(sessionId);
        return s != null && s.isLoggedIn();
    }

    public boolean isListening() {
        WeChatSession s = sessionManager.getDefaultSession();
        return s != null && s.isListening();
    }

    public boolean isListening(String sessionId) {
        WeChatSession s = sessionManager.getSession(sessionId);
        return s != null && s.isListening();
    }

    public String getBotId() {
        WeChatSession s = sessionManager.getDefaultSession();
        return s != null ? s.getBotId() : null;
    }

    // ---- session management ----

    public List<WeChatSessionManager.SessionInfo> listSessions() {
        return sessionManager.listSessionInfos();
    }

    public int getSessionCount() {
        return sessionManager.getSessionCount();
    }

    public void switchDefaultSession(String sessionId) {
        sessionManager.setDefaultSessionId(sessionId);
        System.out.println("Switched to session " + sessionId.substring(0, 8));
    }

    public WeChatSessionManager getSessionManager() {
        return sessionManager;
    }

    public void saveAll() {
        sessionManager.saveAllSessions();
        System.out.println("All sessions saved.");
    }

    public int restoreAll() {
        return sessionManager.restoreSessions();
    }

    public void closeSession(String sessionId) {
        sessionManager.closeSession(sessionId);
    }

    public void deleteSession(String sessionId) {
        sessionManager.deleteSession(sessionId);
    }

    // ---- lifecycle (default session) ----

    public synchronized void logout() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid != null) {
            sessionManager.closeSession(sid);
        }
    }

    public synchronized void logoutAll() {
        sessionManager.closeAll();
        System.out.println("All sessions logged out.");
    }

    // ---- utility methods (used by CliRunner) ----

    /** Extract first text from a message. */
    public static String extractText(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.textItem() != null)
                .findFirst()
                .map(item -> item.textItem().text())
                .orElse(null);
    }

    /** Extract first image item from a message. */
    public static MessageItem extractFirstImage(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.imageItem() != null)
                .findFirst()
                .orElse(null);
    }

    /** Extract first voice item from a message. */
    public static MessageItem extractFirstVoice(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.voiceItem() != null)
                .findFirst()
                .orElse(null);
    }

    /** Extract first file item from a message. */
    public static MessageItem extractFirstFile(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.fileItem() != null)
                .findFirst()
                .orElse(null);
    }

    /** Translate voice recognition technical errors to user-friendly messages. */
    public static String getVoiceErrorMessage(String technicalError) {
        if (technicalError.contains("音频数据为空") || technicalError.contains("The audio is empty")) {
            return "抱歉，我没有听清您说的话，请再试一次（语音不要太短）";
        }
        if (technicalError.contains("未能从响应中提取识别文本")) {
            return "抱歉，语音识别没有返回结果，请再试一次";
        }
        if (technicalError.contains("未配置阿里云 API Key")) {
            return "语音识别服务暂时不可用，请稍后再试";
        }
        if (technicalError.contains("语音识别失败")) {
            return "语音识别失败，请再试一次";
        }
        return "抱歉，语音识别出现问题，请再试一次";
    }

    // ---- helpers ----

    private String requireDefaultSession() {
        String sid = sessionManager.getDefaultSessionId();
        if (sid == null) {
            throw new IllegalStateException("No session. Call login() first.");
        }
        WeChatSession s = sessionManager.getSession(sid);
        if (s == null || !s.isLoggedIn()) {
            throw new IllegalStateException("Not logged in. Call login() first.");
        }
        return sid;
    }
}
