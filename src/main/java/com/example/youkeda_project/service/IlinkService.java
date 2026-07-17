package com.example.youkeda_project.service;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.client.IlinkProtocolClient;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.MessageItem;
import com.example.youkeda_project.protocol.QRCodeResponse;
import com.example.youkeda_project.protocol.WeixinMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IlinkService {

    private static final Logger log = LoggerFactory.getLogger(IlinkService.class);
    private static final long POLL_INTERVAL_MS = 3000;

    private final IlinkProtocolClient client;
    private final WeatherService weatherService;
    private final AIService aiService;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private Thread pollThread;

    public IlinkService(IlinkProtocolClient client, WeatherService weatherService, AIService aiService) {
        this.client = client;
        this.weatherService = weatherService;
        this.aiService = aiService;
    }

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
                    log.info("登录成功, botId={}", result.botId());
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
                            if (text != null && msg.contextToken() != null) {
                                log.info("微信消息 from={}, text={}", fromUser, text);
                                System.out.println("\n[微信] " + fromUser + ": " + text);
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

    private void handleMessage(WeixinMessage msg) {
        try {
            String text = extractText(msg);
            String reply;

            String lowerText = text.toLowerCase().trim();
            if (lowerText.startsWith("weather ")) {
                String city = text.substring("weather ".length()).trim();
                reply = weatherService.queryWeather(city);
                aiService.addExchange(msg.fromUserId(), text, reply);
            } else if (lowerText.startsWith("天气 ")) {
                String city = text.substring("天气 ".length()).trim();
                reply = weatherService.queryWeather(city);
                aiService.addExchange(msg.fromUserId(), text, reply);
            } else {
                reply = aiService.chat(msg.fromUserId(), text);
            }

            client.sendMessage(msg.fromUserId(), msg.contextToken(), List.of(MessageItem.text(reply)));
            log.info("已回复 {}: {}", msg.fromUserId(), reply);

        } catch (Exception e) {
            log.error("处理消息失败", e);
        }
    }

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

    private String extractText(WeixinMessage msg) {
        if (msg.itemList() == null) return null;
        return msg.itemList().stream()
                .filter(item -> item.textItem() != null)
                .findFirst()
                .map(item -> item.textItem().text())
                .orElse(null);
    }
}