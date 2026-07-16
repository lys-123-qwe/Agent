package com.example.youkeda_project.service;

import com.example.youkeda_project.client.IlinkProtocolClient;
import com.example.youkeda_project.protocol.GetUpdatesResponse;
import com.example.youkeda_project.protocol.LoginResult;
import com.example.youkeda_project.protocol.QRCodeResponse;
import com.example.youkeda_project.protocol.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IlinkService {

    private static final Logger log = LoggerFactory.getLogger(IlinkService.class);
    private static final long POLL_INTERVAL_MS = 3000;

    private final IlinkProtocolClient client;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private Thread pollThread;

    public IlinkService(IlinkProtocolClient client) {
        this.client = client;
    }

    public String start() {
        if (connected.get()) {
            return "iLink 已经连接";
        }

        try {
            QRCodeResponse qrCode = client.getQRCode();
            String qrcodeId = qrCode.getQrcode();
            String qrCodeUrl = qrCode.getQrcodeImgContent();

            log.info("二维码ID={}, URL={}", qrcodeId, qrCodeUrl);
            System.out.println("\n请用微信扫码登录:");
            System.out.println(qrCodeUrl + "\n");

            while (true) {
                LoginResult result = client.pollQRStatus(qrcodeId);
                String status = result.getStatus();

                if ("confirmed".equals(status)) {
                    client.setCredentials(result.getBotToken(), result.getBaseurl());
                    connected.set(true);
                    log.info("登录成功, botId={}", result.getBotId());
                    System.out.println("\n[系统] iLink 登录成功! BotID=" + result.getBotId());
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
                    if (resp.getGetUpdatesBuf() != null) {
                        cursor = resp.getGetUpdatesBuf();
                    }
                    if (resp.getMsgs() != null && !resp.getMsgs().isEmpty()) {
                        log.info("收到 {} 条消息", resp.getMsgs().size());
                        for (WeixinMessage msg : resp.getMsgs()) {
                            String fromUser = msg.getFromUserId();
                            String text = extractText(msg);
                            if (text != null) {
                                log.info("微信消息 from={}, text={}", fromUser, text);
                                System.out.println("\n[微信] " + fromUser + ": " + text);
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

    public String sendMessage(String userId, String text) {
        if (!connected.get() || client.getBotToken() == null) {
            return "错误: iLink 未连接，请先执行 ilink login";
        }
        try {
            client.sendMessage(userId, text, null);
            return "已发送";
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return "发送失败: " + e.getMessage();
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
        if (msg.getItemList() == null) return null;
        return msg.getItemList().stream()
                .filter(item -> item.getTextItem() != null)
                .findFirst()
                .map(item -> item.getTextItem().getText())
                .orElse(null);
    }
}