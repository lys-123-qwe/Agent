package com.example.youkeda_project.service;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.IntentResult;
import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.WeatherTool;
import com.example.youkeda_project.client.IlinkProtocolClient;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.QRCodeResponse;
import com.example.youkeda_project.protocol.FileItem;
import com.example.youkeda_project.protocol.VoiceItem;
import com.example.youkeda_project.protocol.WeixinMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * iLink 微信 Bot 核心服务。
 * <p>
 * 管理完整的 iLink 生命周期：扫码登录、消息轮询（长轮询 3s 间隔）、
 * 消息路由分发（天气/AI对话/图片生成/图片识别）、首条消息欢迎机制。
 * <p>
 * 由于 iLink 协议不支持主动推送，采用轮询方式拉取消息。
 * 欢迎消息通过登录时记录 ownerId，在其发送的首条消息时回复。
 */
@Service
public class IlinkService {

    private static final Logger log = LoggerFactory.getLogger(IlinkService.class);
    private static final long POLL_INTERVAL_MS = 3000;

    // 首条消息欢迎语（iLink 不支持 Bot 主动推送，在用户首条消息时回复）
    private static final String WELCOME_TEXT = """
        欢迎使用 Claw助手！\n\n\
        可用功能：\n\
        - AI对话：直接发送消息与我聊天\n\
        - 天气查询：直接告诉我城市名，我会帮你查询天气\n\
        - 图片生成：直接描述你想要生成的图片\n\
        - 图片识别：直接发送图片给我\n\
        - 语音识别：直接发送语音消息，自动转文字后 AI 回复\n\
        - 语音合成：告诉我你想朗读的内容，我帮你合成语音\n\
        - 文件识别：直接发送文件（doc/txt/pdf等），AI 自动提取内容并总结\n\n\
        直接说话就行，不需要任何命令前缀！""";

    private final IlinkProtocolClient client;
    private final WeatherService weatherService;
    private final AIService aiService;
    private final ImageService imageService;
    private final VoiceService voiceService;
    private final TtsService ttsService;
    private final FileService fileService;
    private final WeatherTool weatherTool;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicBoolean welcomeSent = new AtomicBoolean(false);
    private String ownerId;       // 登录者的微信用户 ID，用于识别首条消息
    private Thread pollThread;     // 独立的消息轮询线程

    public IlinkService(IlinkProtocolClient client, WeatherService weatherService,
                        AIService aiService, ImageService imageService, VoiceService voiceService,
                        TtsService ttsService, FileService fileService, WeatherTool weatherTool) {
        this.client = client;
        this.weatherService = weatherService;
        this.aiService = aiService;
        this.imageService = imageService;
        this.voiceService = voiceService;
        this.ttsService = ttsService;
        this.fileService = fileService;
        this.weatherTool = weatherTool;
    }

    /**
     * 启动 iLink 登录流程：
     * 1. 获取 QR 码展示给用户
     * 2. 每 2s 轮询扫码状态
     * 3. 状态 confirmed 后保存凭据，启动消息轮询
     */
    public String start() {
        if (connected.get()) {
            return "iLink 已经连接";
        }

        try {
            QRCodeResponse qrCode = client.getQRCode();
            String qrcodeId = qrCode.qrcode();
            String qrCodeUrl = qrCode.qrcodeImgContent();

            log.info("二维码ID={}, URL={}", qrcodeId, qrCodeUrl);
            System.out.println("\n请用微信扫码登录:");
            System.out.println(qrCodeUrl + "\n");

            while (true) {
                LoginResult result = client.pollQRStatus(qrcodeId);
                String status = result.status();

                if ("confirmed".equals(status)) {
                    client.setCredentials(result.botToken(), result.baseurl());
                    connected.set(true);
                    ownerId = result.userId();
                    welcomeSent.set(false);
                    log.info("登录成功, botId={}, userId={}", result.botId(), ownerId);
                    System.out.println("\n[系统] iLink 登录成功! BotID=" + result.botId());
                    startPolling();
                    return "登录成功";
                } else if ("expired".equals(status)) {
                    return "二维码已过期，请重新执行 ilink login";
                }

                Thread.sleep(2000);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "登录被中断";
        } catch (Exception e) {
            log.error("iLink登录失败", e);
            return "登录失败: " + e.getMessage();
        }
    }

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
    }

    /**
     * 消息路由分发核心逻辑：
     * 1. 首条消息 -> 发送欢迎语（消耗消息）
     * 2. 含图片 -> 调用通义千问 VL 识别
     * 3. 含语音 -> 下载语音→Paraformer 转文字→AI 回复
     * 4. weather / 天气 前缀 -> 和风天气查询
     * 5. 图片生成 -> 通义万相生图 -> CDN 上传
     * 6. 其他 -> DeepSeek AI 对话
     */
    private void handleMessage(WeixinMessage msg) {
        try {
            String fromUser = msg.fromUserId();

            // 首条消息欢迎机制：iLink 不支持 Bot 主动推送，通过登录时记录的 ownerId 判断
            // 首条消息用于发送欢迎语（contextToken 只能使用一次，发完无法再回复同一条消息内容）
            if (!welcomeSent.get() && ownerId != null && ownerId.equals(fromUser)) {
                client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(WELCOME_TEXT)));
                welcomeSent.set(true);
                log.info("已向 {} 发送欢迎消息（消耗首条消息）", fromUser);
                System.out.println("[微信] " + fromUser + ": [系统] 已发送欢迎消息");
                return;
            }

            // 图片识别优先处理
            MessageItem imageItem = extractFirstImage(msg);
            if (imageItem != null) {
                String textPrompt = extractText(msg);
                System.out.println("[微信] " + fromUser + " 发送了图片，正在识别...");
                byte[] imageBytes = client.downloadAndDecryptImage(imageItem);
                String reply = aiService.chatWithImage(fromUser, textPrompt, imageBytes);
                client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(reply)));
                log.info("已回复图片识别 {}: {}", fromUser, reply);
                System.out.println("[微信] " + fromUser + ": " + reply);
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
                String reply = aiService.chat(fromUser, transcribedText);
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
                if (fileSize > FileService.MAX_FILE_SIZE) {
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

            // TTS 多轮状态检查
            TtsService.TtsSession ttsSession = ttsService.getOrCreateSession(fromUser);
            if (ttsSession.state == TtsService.TtsState.WAITING_TEXT) {
                ttsSession.text = text;
                ttsSession.state = TtsService.TtsState.WAITING_VOICE;
                log.info("TTS 已获取文本: {}", text);
                client.sendMessage(fromUser, msg.contextToken(),
                    List.of(MessageItem.text(ttsService.getVoicePrompt())));
                return;
            }
            if (ttsSession.state == TtsService.TtsState.WAITING_VOICE) {
                if ("取消".equals(text.trim())) {
                    ttsService.clearSession(fromUser);
                    client.sendMessage(fromUser, msg.contextToken(),
                        List.of(MessageItem.text("已取消语音合成")));
                    return;
                }
                String voiceId = ttsService.resolveVoice(text);
                if (voiceId != null) {
                    log.info("TTS 音色已选择: {} -> {}", text, voiceId);
                    System.out.println("[系统] 正在合成语音...");
                    TtsService.TtsResult ttsResult = ttsService.synthesizeMp3(ttsSession.text, voiceId);
                    System.out.println("[系统] 语音合成完成，正在上传发送...");
                    client.uploadAndSendFile(fromUser, msg.contextToken(), ttsResult.audioBytes(), "tts.mp3", ttsResult.audioBytes().length);
                    ttsService.clearSession(fromUser);
                    log.info("已发送 TTS 语音给 {}", fromUser);
                    System.out.println("[微信] " + fromUser + ": [语音合成消息]");
                    return;
                }
                // 非音色选择，当作一般对话处理（清除 TTS 会话）
                ttsService.clearSession(fromUser);
            }

            // DeepSeek 判断意图：图片/语音合成/一般对话（天气由 Function Calling 处理）
            IntentResult intent = aiService.detectIntent(text);
            log.info("意图识别: intent={}, city={}, description={}", intent.intent(), intent.city(), intent.description());

            if ("image".equals(intent.intent()) && intent.description() != null) {
                String prompt = intent.description();
                String imageUrl = imageService.generateImage(prompt);
                if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                    client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(imageUrl)));
                    log.info("图片生成失败: {}", imageUrl);
                    return;
                }
                log.info("图片已生成: {}", imageUrl);
                System.out.println("[系统] 图片已生成，正在上传到微信CDN...");
                try {
                    byte[] imageBytes = imageService.downloadImage(imageUrl);
                    client.uploadAndSendImage(fromUser, msg.contextToken(), imageBytes);
                    log.info("已回复图片 {}", fromUser);
                    System.out.println("[系统] 图片已发送到微信");
                } catch (Exception e) {
                    log.error("图片上传发送失败", e);
                    client.sendMessage(fromUser, msg.contextToken(),
                        List.of(MessageItem.text("图片上传失败: " + e.getMessage())));
                }
                // 图片生成结果通知 DeepSeek 生成自然语言回复
                String context = "已根据用户描述生成了图片，图片描述：" + prompt;
                String reply = aiService.generateWithContext(fromUser, text, context);
                client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(reply)));
                log.info("已回复图片文字说明 {}: {}", fromUser, reply);
                return;

            } else if ("tts".equals(intent.intent())) {
                String ttsText = intent.description();
                if (ttsText == null || ttsText.isBlank()) {
                    // 未指定朗读内容，追问
                    ttsSession.state = TtsService.TtsState.WAITING_TEXT;
                    String promptMsg = "请告诉我你想让我朗读什么内容？\n（回复 \"取消\" 取消操作）";
                    client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(promptMsg)));
                    log.info("TTS 追问文本: {}", fromUser);
                    return;
                }
                // 已有朗读内容，让用户选择音色
                ttsSession.text = ttsText;
                ttsSession.state = TtsService.TtsState.WAITING_VOICE;
                client.sendMessage(fromUser, msg.contextToken(),
                    List.of(MessageItem.text("已收到，请选择音色：\n" + ttsService.getVoicePrompt())));
                log.info("TTS 文本已提取: {}", ttsText);
                return;

            } else {
                // 天气查询 + 一般对话 → Function Calling（DeepSeek 自动决定是否调用天气工具）
                System.out.println("[系统] " + fromUser + " 正在等待 AI 回复...");
                String reply = aiService.chat(fromUser, text, List.of(weatherTool));
                client.sendMessage(fromUser, msg.contextToken(), List.of(MessageItem.text(reply)));
                log.info("已回复 {}: {}", fromUser, reply);
                System.out.println("[微信] " + fromUser + ": " + reply);
            }

        } catch (Exception e) {
            log.error("处理消息失败", e);
        }
    }

    /**
     * 断开 iLink 连接，停止轮询线程，清除凭据。
     */
    public String stop() {
        polling.set(false);
        connected.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
        client.setCredentials(null, null);
        log.info("iLink 已断开");
        return "iLink 已断开";
    }

    public boolean isConnected() {
        return connected.get();
    }

    /**
     * 从消息的 itemList 中提取第一段文本内容。
     */
    private String extractText(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.textItem() != null)
                .findFirst()
                .map(item -> item.textItem().text())
                .orElse(null);
    }

    /**
     * 从消息的 itemList 中提取第一张图片项。
     * 纯图片消息不会附带文本，在 polling 中通过 hasImage 单独判断避免漏掉。
     */
    private MessageItem extractFirstImage(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.imageItem() != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 从消息的 itemList 中提取第一条语音项。
     */
    private MessageItem extractFirstVoice(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.voiceItem() != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 从消息的 itemList 中提取第一个文件项。
     */
    private MessageItem extractFirstFile(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.fileItem() != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 将语音识别的技术错误转换为用户友好的提示信息。
     * 技术错误详情记录在日志中，用户只看到友好提示。
     */
    private String getVoiceErrorMessage(String technicalError) {
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
        // 其他未知错误
        return "抱歉，语音识别出现问题，请再试一次";
    }
}