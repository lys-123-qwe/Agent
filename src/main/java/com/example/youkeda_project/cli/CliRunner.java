package com.example.youkeda_project.cli;

import com.example.youkeda_project.ai.*;
import com.example.youkeda_project.service.*;
import com.example.youkeda_project.protocol.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@Component
public class CliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CliRunner.class);

    private static final String WELCOME_TEXT = """
        嗨！我是 Claw 小助手 😊\n\
        查天气、看股票、搜新闻、聊天、画图……有啥需要直接跟我说就行～""";

    /** All tools available for LLM Function Calling. */
    private final List<Tool> TOOLS;

    private final IlinkService ilinkService;
    private final AIService aiService;
    private final ImageService imageService;
    private final VoiceService voiceService;
    private final TtsService ttsService;
    private final FileService fileService;

    // Tool instances
    private final WeatherTool weatherTool;
    private final StockTool stockTool;
    private final LogisticsTool logisticsTool;
    private final HotCommentTool hotCommentTool;
    private final ImageTool imageTool;
    private final TtsTool ttsTool;
    private final SearchTool searchTool;
    private final ExchangeRateTool exchangeRateTool;
    private final DateTimeTool dateTimeTool;
    private final PhoneLocationTool phoneLocationTool;
    private final MusicTool musicTool;
    private final RecommendMusicTool recommendMusicTool;
    private final TranslationTool translationTool;
    private final MemoryTool memoryTool;
    private final FinancialAnalysisTool financialAnalysisTool;

    // Per-user state
    private final Map<String, byte[]> pendingImages = new ConcurrentHashMap<>();
    private final Map<String, String> lastGeneratedImageUrl = new ConcurrentHashMap<>();
    private final Map<String, Boolean> ownerWelcomed = new ConcurrentHashMap<>();

    public CliRunner(IlinkService ilinkService,
                     AIService aiService,
                     ImageService imageService,
                     VoiceService voiceService,
                     TtsService ttsService,
                     FileService fileService,
                     WeatherTool weatherTool,
                     StockTool stockTool,
                     LogisticsTool logisticsTool,
                     HotCommentTool hotCommentTool,
                     ImageTool imageTool,
                     TtsTool ttsTool,
                     SearchTool searchTool,
                     ExchangeRateTool exchangeRateTool,
                     DateTimeTool dateTimeTool,
                     PhoneLocationTool phoneLocationTool,
                     MusicTool musicTool,
                     RecommendMusicTool recommendMusicTool,
                     TranslationTool translationTool,
                     MemoryTool memoryTool,
                     FinancialAnalysisTool financialAnalysisTool) {
        this.ilinkService = ilinkService;
        this.aiService = aiService;
        this.imageService = imageService;
        this.voiceService = voiceService;
        this.ttsService = ttsService;
        this.fileService = fileService;
        this.weatherTool = weatherTool;
        this.stockTool = stockTool;
        this.logisticsTool = logisticsTool;
        this.hotCommentTool = hotCommentTool;
        this.imageTool = imageTool;
        this.ttsTool = ttsTool;
        this.searchTool = searchTool;
        this.exchangeRateTool = exchangeRateTool;
        this.dateTimeTool = dateTimeTool;
        this.phoneLocationTool = phoneLocationTool;
        this.musicTool = musicTool;
        this.recommendMusicTool = recommendMusicTool;
        this.translationTool = translationTool;
        this.memoryTool = memoryTool;
        this.financialAnalysisTool = financialAnalysisTool;

        this.TOOLS = List.of(weatherTool, stockTool, logisticsTool, hotCommentTool,
                imageTool, ttsTool, searchTool, exchangeRateTool, dateTimeTool,
                phoneLocationTool, musicTool, recommendMusicTool,
                translationTool, memoryTool);
    }

    @Override
    public void run(String... args) {
        log.info("Application started");
        System.out.println("Welcome to Claw CLI!");

        // Restore persisted sessions
        try {
            int restored = ilinkService.restoreAll();
            if (restored > 0) {
                System.out.println("Restored " + restored + " session(s), starting listeners...");
                for (var info : ilinkService.listSessions()) {
                    if ("LOGGED_IN".equals(info.status())) {
                        startMessageListener(info.sessionId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Session restore failed: {}", e.getMessage());
        }

        // Auto-login on startup
        try {
            handleWeChatLogin(null, true);
        } catch (Exception e) {
            log.error("Auto login failed: {}", e.getMessage());
            System.out.println("Auto login failed, type 'wechat login' to retry.");
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            try {
                if (input.isEmpty()) continue;

                String[] parts = input.split("\\s+", 2);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "help" -> showHelp();
                    case "status" -> showStatus();
                    case "weather" -> handleWeather(parts.length > 1 ? parts[1] : null);
                    case "stock" -> handleStock(parts.length > 1 ? parts[1] : null);
                    case "logistics" -> handleLogistics(parts.length > 1 ? parts[1] : null);
                    case "hotcomment" -> handleHotComment();
                    case "chat" -> handleChat(parts.length > 1 ? parts[1] : null);
                    case "chatimg" -> handleChatImage(parts.length > 1 ? parts[1] : null);
                    case "imagine" -> handleImagine(parts.length > 1 ? parts[1] : null);
                    case "tts" -> handleTts(parts.length > 1 ? parts[1] : null);
                    case "voices" -> handleVoices();
                    case "wechat" -> handleWeChat(parts.length > 1 ? parts[1] : null);
                    case "exit", "quit" -> {
                        ilinkService.logoutAll();
                        System.out.println("Goodbye!");
                        return;
                    }
                    default -> System.out.println("Unknown command. Type 'help'.");
                }
            } catch (Exception e) {
                log.error("Command error: {}", e.getMessage());
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    // ==================== Help & Status ====================

    private void showHelp() {
        System.out.println("""
                Commands:
                  weather <city>              - Get weather
                  stock <name/code>           - Query stock (A/HK/US)
                  logistics <number> [courier] - Track package delivery
                  hotcomment                  - Random NetEase hot comment
                  chat <message>              - AI chat
                  chatimg <url> <text>        - Image recognition
                  imagine <description>       - Generate image
                  tts <text>                  - Text to speech
                  tts <N> <text>              - TTS with timbre #N (1-10)
                  voices                      - List voice timbres
                  status                      - Show status
                  wechat login [label]        - Start QR login
                  wechat sessions             - List all sessions
                  wechat switch <id>          - Switch default session
                  wechat stop [id]            - Stop listener
                  wechat close <id>           - Close session
                  wechat logout               - Logout default
                  wechat logout-all           - Logout all
                  wechat save                 - Save sessions
                  wechat restore              - Restore sessions
                  exit / quit                 - Exit
                """);
    }

    private void showStatus() {
        int count = ilinkService.getSessionCount();
        if (count == 0) {
            System.out.println("WeChat: no active sessions. Type 'wechat login' to start.");
        } else {
            System.out.println("WeChat sessions (" + count + " total):");
            for (var info : ilinkService.listSessions()) {
                String marker = info.sessionId().equals(ilinkService.getSessionManager().getDefaultSessionId())
                        ? " ★" : "  ";
                System.out.printf("  %s %s%-20s status=%-10s botId=%s listening=%s%n",
                        info.shortId(), marker, info.label(), info.status(),
                        info.botId() != null ? info.botId() : "N/A", info.listening());
            }
            System.out.println("  ★ = default session");
        }
        System.out.println("Tools: " + TOOLS.stream().map(Tool::getName).toList());
    }

    // ==================== Direct tool commands ====================

    private void handleWeather(String city) {
        if (city == null || city.isBlank()) {
            System.out.println("Usage: weather <city>");
            return;
        }
        System.out.println(weatherTool.execute(Map.of("location", city)).getText());
    }

    private void handleStock(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            System.out.println("Usage: stock <name or code>");
            return;
        }
        System.out.println(stockTool.execute(Map.of("stock", keyword)).getText());
    }

    private void handleLogistics(String arg) {
        if (arg == null || arg.isBlank()) {
            System.out.println("Usage: logistics <trackingNumber> [courier]");
            return;
        }
        String[] parts = arg.split("\\s+", 2);
        String number = parts[0];
        String courier = parts.length > 1 ? parts[1] : null;
        System.out.println(logisticsTool.execute(Map.of("number", number, "courier", courier)).getText());
    }

    private void handleHotComment() {
        System.out.println(hotCommentTool.execute(Map.of()).getText());
    }

    private void handleChat(String message) {
        if (message == null || message.isBlank()) {
            System.out.println("Usage: chat <message>");
            return;
        }
        System.out.print("Thinking... ");
        ChatResult result = aiService.chat("CLI", message, TOOLS);
        System.out.println("\n" + result.getReplyText());
    }

    private void handleChatImage(String arg) {
        if (arg == null || arg.isBlank()) {
            System.out.println("Usage: chatimg <imageUrl> <text>");
            return;
        }
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            System.out.println("Usage: chatimg <imageUrl> <text>");
            return;
        }
        System.out.print("Analyzing... ");
        try {
            byte[] imageBytes = imageService.downloadImage(parts[0]);
            String reply = aiService.chatWithImage("CLI", parts[1], imageBytes);
            System.out.println("\n" + reply);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleImagine(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            System.out.println("Usage: imagine <description>");
            return;
        }
        System.out.print("Generating... ");
        System.out.println("\n" + imageTool.execute(Map.of("prompt", prompt)).getText());
    }

    private void handleTts(String args) {
        if (args == null || args.isBlank()) {
            System.out.println("Usage: tts <text>  or  tts <N> <text>  (N = 1~10)");
            return;
        }

        String voice = ttsService.getDefaultVoice();
        String text;

        String[] parts = args.split("\\s+", 2);
        if (ttsService.resolveVoice(parts[0]) != null) {
            voice = ttsService.resolveVoice(parts[0]);
            if (parts.length < 2) {
                System.out.println("Usage: tts <N> <text> — text is required");
                return;
            }
            text = parts[1];
        } else {
            text = args;
        }

        String voiceLabel = voice;
        var voices = ttsService.getVoices();
        for (var v : voices) {
            if (v.voiceId().equals(voice)) {
                voiceLabel = v.name() + " (" + v.description() + ")";
                break;
            }
        }

        System.out.print("Generating speech [" + voiceLabel + "]... ");
        var ttsResult = ttsService.synthesizeMp3(text, voice);
        System.out.printf("\nGenerated %d bytes (≈ %d seconds)%n",
                ttsResult.audioBytes().length, ttsResult.durationSeconds());
        try {
            Path outPath = Paths.get("voice_output.mp3");
            Files.write(outPath, ttsResult.audioBytes());
            System.out.println("Saved to: " + outPath.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("Could not save file: " + e.getMessage());
        }
    }

    private void handleVoices() {
        var voices = ttsService.getVoices();
        System.out.println("\n  Available timbres:\n");
        System.out.printf("  %-4s %-20s %s%n", "#", "Name", "Description");
        System.out.println("  " + "-".repeat(50));
        for (int i = 0; i < voices.size(); i++) {
            var v = voices.get(i);
            boolean isDefault = v.voiceId().equals(ttsService.getDefaultVoice());
            String marker = isDefault ? " ★ default" : "";
            System.out.printf("  %-4d %-20s %s%s%n", i + 1, v.name(), v.description(), marker);
        }
    }

    // ==================== WeChat commands ====================

    private void handleWeChat(String args) {
        if (args == null || args.isBlank()) {
            System.out.println("""
                    Usage: wechat <command> [args]
                      login [label]    - Start QR login (new bot session)
                      sessions         - List all sessions
                      switch <id>      - Switch default session
                      stop [id]        - Stop listener (default or by id)
                      close <id>       - Close a session
                      logout           - Logout default session
                      logout-all       - Logout ALL sessions
                      save             - Persist all sessions to disk
                      restore          - Restore sessions from disk""");
            return;
        }

        String[] parts = args.split("\\s+", 2);
        switch (parts[0].toLowerCase()) {
            case "login" -> {
                String label = parts.length > 1 ? parts[1] : null;
                handleWeChatLogin(label, false);
            }
            case "sessions" -> showStatus();
            case "switch" -> {
                if (parts.length < 2) {
                    System.out.println("Usage: wechat switch <sessionId-prefix>");
                    return;
                }
                handleWeChatSwitch(parts[1]);
            }
            case "stop" -> {
                if (parts.length > 1) {
                    ilinkService.stopListening(resolveSessionId(parts[1]));
                } else {
                    ilinkService.stopListening();
                }
            }
            case "close" -> {
                if (parts.length < 2) {
                    System.out.println("Usage: wechat close <sessionId-prefix>");
                    return;
                }
                handleWeChatClose(parts[1]);
            }
            case "logout" -> {
                ilinkService.logout();
                System.out.println("Default session logged out.");
            }
            case "logout-all" -> ilinkService.logoutAll();
            case "save" -> ilinkService.saveAll();
            case "restore" -> {
                int n = ilinkService.restoreAll();
                System.out.println("Restored " + n + " session(s).");
            }
            default -> System.out.println("Unknown: " + parts[0]);
        }
    }

    private void handleWeChatLogin(String label, boolean forceNew) {
        String qrContent;
        String sessionId;
        String sessionLabel;

        if (label != null && !label.isBlank()) {
            var resp = ilinkService.login(label);
            qrContent = resp.qrContent();
            sessionId = resp.sessionId();
            sessionLabel = resp.label();
            System.out.println("\n[" + sessionLabel + "] " + resp.sessionId().substring(0, 8));
        } else if (forceNew) {
            var resp = ilinkService.loginNew();
            qrContent = resp.qrContent();
            sessionId = resp.sessionId();
            sessionLabel = resp.label();
        } else {
            qrContent = ilinkService.login();
            sessionId = ilinkService.getSessionManager().getDefaultSessionId();
            sessionLabel = "default";
            if (sessionId == null || qrContent.startsWith("Already") || qrContent.startsWith("QR login already")) {
                System.out.println(qrContent);
                return;
            }
        }

        System.out.println("\nQR Code URL:\n" + qrContent);
        System.out.println("\nWaiting for WeChat scan (" + sessionLabel + ")...");
        try {
            var result = ilinkService.waitForLogin(sessionId, 120000);
            System.out.println("Login OK! Bot: " + result.botId());
            System.out.println("Auto-starting listener...");
            startMessageListener(sessionId);
        } catch (TimeoutException e) {
            System.out.println("Login timeout. Type 'wechat login' to retry.");
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
        }
    }

    private void handleWeChatSwitch(String idPrefix) {
        String sid = resolveSessionId(idPrefix);
        ilinkService.switchDefaultSession(sid);
    }

    private void handleWeChatClose(String idPrefix) {
        String sid = resolveSessionId(idPrefix);
        ilinkService.deleteSession(sid);
        System.out.println("Closed session " + sid.substring(0, 8));
    }

    private String resolveSessionId(String prefix) {
        var infos = ilinkService.listSessions();
        var matches = infos.stream()
                .filter(i -> i.sessionId().startsWith(prefix) || i.shortId().equals(prefix))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No session matching '" + prefix + "'");
        }
        if (matches.size() > 1) {
            System.out.println("Multiple matches:");
            for (var m : matches) {
                System.out.println("  " + m.shortId() + " — " + m.label());
            }
            throw new IllegalArgumentException("Ambiguous prefix '" + prefix + "'");
        }
        return matches.get(0).sessionId();
    }

    // ==================== Message listener (auto-reply) ====================

    private void startMessageListener(String sessionId) {
        ilinkService.startListening(sessionId,
            // Text handler
            (userId, text, contextToken) -> {
                long t0 = System.currentTimeMillis();
                log.info("[{}] text from={}: {}", sessionId.substring(0, 8), userId,
                        text.length() > 50 ? text.substring(0, 50) + "..." : text);

                // Check for pending image
                byte[] pendingImage = pendingImages.remove(userId);
                if (pendingImage != null) {
                    return processImageInstruction(sessionId, userId, contextToken, pendingImage, text);
                }

                // Check for last generated image
                String lastGenUrl = lastGeneratedImageUrl.get(userId);
                if (lastGenUrl != null) {
                    try {
                        byte[] genBytes = imageService.downloadImage(lastGenUrl);
                        return processImageInstruction(sessionId, userId, contextToken, genBytes, text);
                    } catch (Exception e) {
                        log.warn("Download last generated image failed: {}", e.getMessage());
                        lastGeneratedImageUrl.remove(userId);
                    }
                }

                // Normal text → AI with Function Calling
                List<Tool> allTools = List.of(weatherTool, imageTool, ttsTool, searchTool, stockTool,
                    exchangeRateTool, dateTimeTool, phoneLocationTool,
                    musicTool, recommendMusicTool, translationTool, hotCommentTool, logisticsTool,
                    memoryTool, financialAnalysisTool);

                ChatResult result = aiService.chat(userId, text, allTools);
                String reply = result.getReplyText();

                // Handle media
                reply = handleMediaResult(sessionId, userId, contextToken, result, reply);

                // Welcome message for owner
                var session = ilinkService.getSessionManager().getSession(sessionId);
                if (session != null && session.getUserId() != null
                        && session.getUserId().equals(userId)
                        && !ownerWelcomed.getOrDefault(sessionId, false)) {
                    reply = WELCOME_TEXT + "\n\n" + reply;
                    ownerWelcomed.put(sessionId, true);
                }

                long totalDuration = System.currentTimeMillis() - t0;
                log.info("[{}] text handled in {}ms", sessionId.substring(0, 8), totalDuration);
                return reply;
            },
            // Image handler
            (userId, prompt, imageBytes, contextToken) -> {
                pendingImages.put(userId, imageBytes);
                return "看到啦～你想让我帮你看看这张图，还是按你的想法改一改？";
            },
            // Voice handler
            (userId, audioBytes, contextToken) -> {
                System.out.println("[微信] " + userId + " 发送了语音，正在识别...");
                String transcribedText = voiceService.transcribe(audioBytes);
                if (transcribedText.startsWith("错误:")) {
                    log.warn("语音识别失败: {}", transcribedText);
                    return IlinkService.getVoiceErrorMessage(transcribedText);
                }
                log.info("语音转文字: {}", transcribedText);
                System.out.println("[微信] " + userId + " (语音): " + transcribedText);

                List<Tool> voiceTools = List.of(weatherTool, imageTool, ttsTool, searchTool,
                    stockTool, musicTool, recommendMusicTool, hotCommentTool,
                    logisticsTool, memoryTool, financialAnalysisTool);
                ChatResult result = aiService.chat(userId, transcribedText, voiceTools);
                String reply = result.getReplyText();

                reply = handleMediaResult(sessionId, userId, contextToken, result, reply);
                return reply;
            },
            // File handler
            (userId, fileName, fileBytes, prompt, contextToken) -> {
                System.out.println("[微信] " + userId + " 发送了文件: " + fileName
                        + " (" + fileBytes.length + " bytes)");
                try {
                    if (fileBytes.length > fileService.getMaxFileSize()) {
                        return "文件太大了，我只能处理 20MB 以内的文件";
                    }
                    String extractedText = fileService.extractText(fileBytes, fileName);
                    log.info("文件文本提取: {} {}chars", fileName, extractedText.length());
                    String reply = aiService.generateWithContext(userId,
                        "请帮我总结这个文件的内容", "文件名：" + fileName + "\n\n文件内容：\n" + extractedText);
                    return reply;
                } catch (Exception e) {
                    log.error("文件处理失败: {}", fileName, e);
                    return "文件处理失败: " + e.getMessage();
                }
            }
        );
    }

    private String processImageInstruction(String sessionId, String userId, String contextToken,
                                           byte[] imageBytes, String instruction) {
        System.out.println("[系统] " + userId + " 正在处理图片指令...");

        RecognizeImageTool recognizeTool = new RecognizeImageTool(aiService, userId, imageBytes);
        EditImageTool editTool = new EditImageTool(imageService, imageBytes);
        List<Tool> imgTools = new ArrayList<>(List.of(weatherTool, imageTool, ttsTool, searchTool,
            musicTool, recommendMusicTool, hotCommentTool, logisticsTool));
        imgTools.add(recognizeTool);
        imgTools.add(editTool);

        String contextMessage = "[系统提示：当前有一张待处理的图片。请根据用户的指令选择合适的工具。]\n"
            + "[如果用户想识别图片内容（这是什么、描述图片、提取文字等），请调用 recognize_image 工具]\n"
            + "[如果用户想编辑图片（修改物体、颜色、背景、风格等），请调用 edit_image 工具]\n"
            + "用户指令：" + instruction;

        ChatResult result = aiService.chat(userId, contextMessage, imgTools);
        String reply = result.getReplyText();

        // Handle edited image
        if (result.hasPendingImage()) {
            byte[] toolImageBytes = result.getFirstPendingImageBytes();
            String imageUrl = toolImageBytes == null ? result.getFirstPendingImageUrl() : null;
            if (imageUrl != null) lastGeneratedImageUrl.put(userId, imageUrl);
            System.out.println("[系统] 图片编辑完成，正在上传...");
            try {
                byte[] uploadBytes = toolImageBytes != null ? toolImageBytes : imageService.downloadImage(imageUrl);
                ilinkService.sendImage(sessionId, userId, contextToken, uploadBytes);
                log.info("已发送编辑后的图片");
            } catch (Exception e) {
                log.error("图片上传失败", e);
                reply += "\n\n（图片上传失败: " + e.getMessage() + "）";
            }
        }

        return reply;
    }

    /** Handle media side-effects from AI tool results. Returns augmented reply text. */
    private String handleMediaResult(String sessionId, String userId, String contextToken,
                                     ChatResult result, String reply) {
        if (result.hasPendingImage()) {
            byte[] imageBytes = result.getFirstPendingImageBytes();
            String imageUrl = imageBytes == null ? result.getFirstPendingImageUrl() : null;
            if (imageUrl != null) lastGeneratedImageUrl.put(userId, imageUrl);
            System.out.println("[系统] 图片已生成，正在上传...");
            try {
                if (imageBytes == null) {
                    imageBytes = imageService.downloadImage(imageUrl);
                }
                ilinkService.sendImage(sessionId, userId, contextToken, imageBytes);
                log.info("已发送图片到 {}", userId);
            } catch (Exception e) {
                log.error("图片上传失败", e);
                reply += "\n\n（图片上传失败: " + e.getMessage() + "）";
            }
        }

        if (result.hasPendingAudio()) {
            ToolResult audio = result.getFirstPendingAudio();
            System.out.println("[系统] 语音合成完成，正在上传...");
            try {
                String fileName = audio.getAudioFileName() != null ? audio.getAudioFileName() : "audio.mp3";
                ilinkService.sendFile(sessionId, userId, contextToken,
                    audio.getAudioBytes(), fileName, (long) audio.getAudioBytes().length);
                log.info("已发送音频文件 ({}s): {}", audio.getAudioDurationSeconds(), fileName);
            } catch (Exception e) {
                log.error("音频上传失败", e);
                reply += "\n\n（音频上传失败: " + e.getMessage() + "）";
            }
        }

        return reply;
    }
}
